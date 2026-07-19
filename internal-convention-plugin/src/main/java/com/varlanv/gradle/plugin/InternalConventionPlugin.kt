package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
class InternalConventionPlugin : Plugin<Project> {

    private class Impl(private val project: Project) {
        private val extensions = project.extensions
        private val providers = project.providers
        private val pluginManager = project.pluginManager
        private val tasks = project.tasks
        private val repositories = project.repositories
        private val dependencies = project.dependencies
        private val internalEnvironment = extensions.findByName(InternalEnvironment.NAME) as InternalEnvironment?
            ?: InternalEnvironment(providers.environmentVariable("CI").isPresent, false)
        private val internalCatalog = extensions.findByName(InternalCatalog.NAME) as InternalCatalog?
            ?: InternalCatalog(extensions.getByName("versionCatalogs") as VersionCatalogsExtension)
        private val internalConventionExtension =
            extensions.findByName(InternalConventionExtension.NAME) as InternalConventionExtension?
                ?: extensions.create(InternalConventionExtension.NAME, InternalConventionExtension::class.java)
        private val kotlinMinorMatrixExtension =
            extensions.findByName(KotlinMinorMatrixExtension.NAME) as KotlinMinorMatrixExtension?
                ?: extensions.create(KotlinMinorMatrixExtension.NAME, KotlinMinorMatrixExtension::class.java)
        private val javaToolchainVersion = internalCatalog.getVersion("javaToolchainVersion")
        private val javaTargetVersion = internalCatalog.getVersion("javaTargetVersion")
        private val kotlinVersion = internalCatalog.getVersion("kotlinVersion")
        private val jvmVendor = JvmVendorSpec.ADOPTIUM

        fun run() {
            configureRepositories()
            pluginManager.apply(internalCatalog.getPlugin("kotlin-jvm").get().pluginId)
            pluginManager.apply("maven-publish")
            project.afterEvaluate {
                applyCommonPlugins()
                configureKotlin()
                configureCommonDependencies()
                configureTests()
                configureKotlinMinorMatrix()
                configureWrasse()
                configureWrasseApply()
                configurePublishing()
            }
        }

        fun applyCommonPlugins() {
            if (internalEnvironment.isLocal()) {
                pluginManager.apply(IdeaPlugin::class.java)
                extensions.configure<IdeaModel>("idea") {
                    it.module.isDownloadJavadoc = true
                    it.module.isDownloadSources = true
                }
            }
            val isTestingModule = project.path.startsWith(":testing:")
            tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
                kotlinCompile.compilerOptions {
                    if (isTestingModule || kotlinCompile.name.contains("Test")) {
                        jvmTarget.set(JvmTarget.fromTarget(javaToolchainVersion))
                    } else {
                        jvmTarget.set(JvmTarget.fromTarget(javaTargetVersion))
                    }
                    allWarningsAsErrors.set(true)
                    extraWarnings.set(true)
                    progressiveMode.set(true)
                    freeCompilerArgs.addAll("-Xjsr305=strict")
                }
            }
        }

        fun configureCommonDependencies() {
            dependencies.add(
                "testImplementation",
                dependencies.create("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            )
            if (project.name != "common-test") {
                dependencies.add(
                    "testImplementation",
                    dependencies.project(mapOf("path" to ":testing:common-test"))
                )
            }
        }

        fun configureRepositories() {
            if (internalEnvironment.isLocal()) {
                repositories.add(repositories.mavenLocal())
            }
            repositories.add(repositories.mavenCentral())
        }

        fun configureKotlin() {
            val isTestingModule = project.path.startsWith(":testing:")
            tasks.withType(org.gradle.api.tasks.compile.JavaCompile::class.java) { javaCompile ->
                if (isTestingModule || javaCompile.name.contains("Test")) {
                    javaCompile.sourceCompatibility = javaToolchainVersion
                    javaCompile.targetCompatibility = javaToolchainVersion
                } else {
                    javaCompile.sourceCompatibility = javaTargetVersion
                    javaCompile.targetCompatibility = javaTargetVersion
                }
            }
            extensions.configure<KotlinJvmProjectExtension>("kotlin") { kotlin ->
                kotlin.jvmToolchain { jvmToolchain ->
                    jvmToolchain.languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
                    jvmToolchain.vendor.set(jvmVendor)
                }
            }
        }

        fun configureTests() {
            tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach { test ->
                test.useJUnitPlatform()
                test.testLogging { logging ->
                    logging.showStandardStreams = true
                    logging.showStackTraces = true
                }
            }
        }

        fun configureKotlinMinorMatrix() {
            if (!kotlinMinorMatrixExtension.minor.isPresent) {
                return
            }
            val minor = kotlinMinorMatrixExtension.minor.get()
            val patchVersions = kotlinMinorMatrixExtension.patches.get()
            val latestMinor = patchVersions.last()
            val isCurrentKotlin = latestMinor == kotlinVersion

            dependencies.add(
                "testImplementation",
                dependencies.project(mapOf("path" to ":testing:wrasse-kotlinc-plugin-tests-base"))
            )

            if (!isCurrentKotlin) {
                project.configurations.named("testRuntimeClasspath").configure { config ->
                    config.resolutionStrategy.force("org.jetbrains.kotlin:kotlin-compiler-embeddable:$latestMinor")
                }
            }

            val targetStdlibConfigs = (patchVersions + latestMinor).distinct().associateWith { version ->
                project.configurations.create("targetStdlib_${version.replace('.', '_')}") { config ->
                    config.isCanBeConsumed = false
                    config.isCanBeResolved = true
                }
            }
            for ((version, config) in targetStdlibConfigs) {
                dependencies.add(config.name, "org.jetbrains.kotlin:kotlin-stdlib:$version")
            }
            fun stdlibJarPath(version: String): String =
                targetStdlibConfigs.getValue(version).filter { it.name.startsWith("kotlin-stdlib-") }.singleFile.absolutePath

            val fixturesDir =
                project.rootProject.file("testing/wrasse-test-harness/src/main/resources/fixtures").absolutePath
            tasks.withType(Test::class.java).configureEach { it.systemProperty("wrasse.fixtures.dir", fixturesDir) }

            val testTask = tasks.named("test", Test::class.java)
            if (!isCurrentKotlin) {
                testTask.configure { it.enabled = false }
            }

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)

            if (isCurrentKotlin) {
                tasks.register("testMinor") { task ->
                    task.group = "verification"
                    task.description = "Run fixture tests against Kotlin $latestMinor"
                    task.dependsOn(testTask)
                }
            } else {
                tasks.register("testMinor", Test::class.java) { task ->
                    task.group = "verification"
                    task.description = "Run fixture tests against Kotlin $latestMinor"
                    task.testClassesDirs = sourceSets.getByName("test").output.classesDirs
                    task.classpath = sourceSets.getByName("test").runtimeClasspath
                    task.doFirst { task.systemProperty("wrasse.harness.stdlibPath", stdlibJarPath(latestMinor)) }
                }
            }

            for (version in patchVersions) {
                val safeName = version.replace('.', '_')
                val patchConfig = project.configurations.create("kotlincPatch_$safeName") { config ->
                    config.isCanBeConsumed = false
                    config.isCanBeResolved = true
                }
                dependencies.add(patchConfig.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:$version")
                tasks.register("testPatch_$safeName", Test::class.java) { task ->
                    task.group = "verification"
                    task.description = "Run fixture tests against Kotlin $version"
                    task.testClassesDirs = sourceSets.getByName("test").output.classesDirs
                    task.classpath = project.files(patchConfig) + sourceSets.getByName("test").runtimeClasspath.filter {
                        !it.name.startsWith("kotlin-compiler-embeddable")
                    }
                    task.doFirst { task.systemProperty("wrasse.harness.stdlibPath", stdlibJarPath(version)) }
                }
            }

            tasks.register("testPatchHarness") { task ->
                task.group = "verification"
                task.description = "Run fixture tests against all Kotlin $minor.x patch versions"
                for (version in patchVersions) {
                    task.dependsOn(tasks.named("testPatch_${version.replace('.', '_')}"))
                }
            }
        }

        fun configurePublishing() {
            extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") { publishing ->
                if (publishing.publications.findByName("maven") == null) {
                    publishing.publications.create("maven", org.gradle.api.publish.maven.MavenPublication::class.java) {
                        it.from(project.components.getByName("java"))
                    }
                }
            }
        }

        fun configureWrasseApply() {
            val wrasseApplyClasspath = project.configurations.create("wrasseApplyClasspath") { config ->
                config.isCanBeConsumed = false
                config.isCanBeResolved = true
            }
            dependencies.add(wrasseApplyClasspath.name, internalCatalog.getLib("wrasse-compiler-plugin"))

            tasks.register("wrasseApply", JavaExec::class.java) { task ->
                task.group = "verification"
                task.classpath = project.files(wrasseApplyClasspath)
                task.mainClass.set("com.varlanv.wrasse.lang.WPatchApplierKt")
                task.args(project.layout.buildDirectory.dir("wrasse").get().asFile.absolutePath)
                task.isIgnoreExitValue = false
                task.onlyIf {
                    java.io.File(project.layout.buildDirectory.dir("wrasse").get().asFile, "wrasse-fixes.txt").exists()
                }
            }
        }

        fun configureWrasse() {
            if (!providers.gradleProperty("wrasseCheck").isPresent) {
                return
            }
            val wrasseLib = internalCatalog.getLib("wrasse-compiler-plugin")
            dependencies.add("kotlinCompilerPluginClasspath", wrasseLib)

            val wrasseFix = providers.gradleProperty("wrasseFix")
            if (wrasseFix.isPresent) {
                val fixOutputDir = project.layout.buildDirectory.dir("wrasse").get().asFile.absolutePath
                tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
                    kotlinCompile.compilerOptions {
                        freeCompilerArgs.addAll(
                            "-P", "plugin:com.varlanv.wrasse:fix=true",
                            "-P", "plugin:com.varlanv.wrasse:fixOutputDir=$fixOutputDir",
                        )
                    }
                }
            }
        }
    }

    override fun apply(target: Project) {
        Impl(target).run()
    }
}
