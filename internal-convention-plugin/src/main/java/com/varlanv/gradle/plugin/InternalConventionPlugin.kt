package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
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
        private val javaToolchainVersion = internalCatalog.getVersion("javaToolchainVersion")
        private val javaTargetVersion = internalCatalog.getVersion("javaTargetVersion")
        private val kotlinVersion = internalCatalog.getVersion("kotlinVersion")
        private val jvmVendor = JvmVendorSpec.ADOPTIUM

        fun run() {
            configureRepositories()
            pluginManager.apply(internalCatalog.getPlugin("kotlin-jvm").get().pluginId)
            project.afterEvaluate {
                applyCommonPlugins()
                configureKotlin()
                configureCommonDependencies()
                configureTests()
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
    }

    override fun apply(target: Project) {
        Impl(target).run()
    }
}
