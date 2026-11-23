package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private val compileTaskNamePattern = Regex("^compile(.*)Kotlin$")

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
            val isFixtureModule = project.path == ":testing:wrasse-kotlinc-plugin-tests"
            val fixturesDir =
                project.rootProject.file("testing/wrasse-test-harness/src/main/resources/fixtures").absolutePath
            tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach { test ->
                test.useJUnitPlatform()
                test.testLogging { logging ->
                    logging.showStandardStreams = true
                    logging.showStackTraces = true
                }
                if (isFixtureModule) {
                    test.systemProperty("wrasse.fixtures.dir", fixturesDir)
                    test.maxHeapSize = "2g"
                    test.jvmArgs("-Xss8m", "-XX:MaxMetaspaceSize=1g")
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

            val applierClasspath = project.files(wrasseApplyClasspath)
            val wrasseDirFile = project.layout.buildDirectory.dir("wrasse").get().asFile
            tasks.register("wrasseApply") { task ->
                task.group = "verification"
                task.description = "Applies the wrasse patch journals of this project in-process"
                task.onlyIf { wrasseDirFile.exists() }
                task.doLast { applyWrassePatches(wrasseDirFile, applierClasspath.files) }
            }

            val pattern = compileTaskNamePattern
            val wrasseDir = project.layout.buildDirectory.dir("wrasse").get().asFile
            val requestDirs = project.provider {
                tasks.withType(KotlinCompile::class.java).names.mapNotNull { name ->
                    pattern.matchEntire(name)?.groupValues?.get(1)?.replaceFirstChar { it.lowercaseChar() }?.ifEmpty { "main" }
                }.map { java.io.File(wrasseDir, it) }
            }
            val debugPerformance = providers.gradleProperty("wrasseDebugPerformance").isPresent
            val formatRequest = tasks.register("wrasseFormatRequest") { task ->
                task.group = "verification"
                task.description = "Marks the next wrasse check compile of this project as a format run"
                task.doLast {
                    val content = "timestamp=${System.currentTimeMillis()}\nformatting=true\n" +
                        (if (debugPerformance) "debugPerformance=true\n" else "")
                    for (dir in requestDirs.get()) {
                        dir.mkdirs()
                        java.io.File(dir, "format-request").writeText(content)
                    }
                }
            }
            tasks.withType(KotlinCompile::class.java).configureEach { it.mustRunAfter(formatRequest) }
        }

        fun configureWrasse() {
            if (!providers.gradleProperty("wrasseCheck").isPresent) {
                return
            }
            val wrasseLib = internalCatalog.getLib("wrasse-compiler-plugin")
            dependencies.add("kotlinCompilerPluginClasspath", wrasseLib)

            tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
                val compilationName = compilationNameFor(kotlinCompile.name) ?: return@withType
                val fixOutputDir = project.layout.buildDirectory.dir("wrasse/$compilationName").get().asFile.absolutePath
                kotlinCompile.outputs.dir(java.io.File(fixOutputDir, "patch")).withPropertyName("wrassePatch")
                wrasseConfigFile(project.projectDir)?.let {
                    kotlinCompile.inputs.file(it).withPathSensitivity(PathSensitivity.NONE).withPropertyName("wrasseConfig")
                }
                kotlinCompile.compilerOptions {
                    freeCompilerArgs.addAll(
                        "-P", "plugin:com.varlanv.wrasse:fixOutputDir=$fixOutputDir",
                    )
                }
            }
        }

        private fun compilationNameFor(compileTaskName: String): String? {
            val match = compileTaskNamePattern.matchEntire(compileTaskName) ?: return null
            return match.groupValues[1].replaceFirstChar { it.lowercaseChar() }.ifEmpty { "main" }
        }
    }

    override fun apply(target: Project) {
        Impl(target).run()
    }
}

private fun wrasseConfigFile(start: java.io.File): java.io.File? =
    generateSequence(start) { it.parentFile }
        .flatMap { dir -> sequenceOf(java.io.File(dir, "wrasse.json"), java.io.File(dir, "wrasse.jsonc")) }
        .firstOrNull { it.isFile }

private fun applyWrassePatches(wrasseDir: java.io.File, classpath: Set<java.io.File>) {
    val files = wrasseDir.walkTopDown().filter { it.isFile }.toList()
    val hasEntries = files.any { f -> f.name == "wrasse-fixes.txt" && f.useLines { lines -> lines.any { it.startsWith("file:") } } }
    if (!hasEntries) {
        files.filter { it.name == "format-request" }.forEach { it.delete() }
        return
    }
    val urls = classpath.map { it.toURI().toURL() }.toTypedArray()
    java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
        val entry = Class.forName("com.varlanv.wrasse.lang.WPatchApplierKt", true, loader)
        val exitCode = entry.getMethod("runApplier", List::class.java).invoke(null, listOf(wrasseDir.absolutePath)) as Int
        if (exitCode != 0) throw org.gradle.api.GradleException("wrasseApply failed for ${wrasseDir.absolutePath}")
    }
}
