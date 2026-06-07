package com.varlanv.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.logging.Logging
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
class InternalKonventionPlugin : Plugin<Project> {

    companion object {
        private val log = Logging.getLogger("internal-convention")
    }

    private class Impl(private val project: Project) {
        private val extensions = project.extensions
        private val providers = project.providers
        private val pluginManager = project.pluginManager
        private val tasks = project.tasks
        private val repositories = project.repositories
        private val dependencies = project.dependencies
        private val internalEnvironment = extensions.findByName(InternalEnvironment.NAME) as InternalEnvironment?
            ?: InternalEnvironment(providers.environmentVariable("CI").isPresent, false)
        private val internalProperties = extensions.findByName(InternalProperties.NAME) as InternalProperties?
            ?: InternalProperties(extensions.getByName("versionCatalogs") as VersionCatalogsExtension)
        private val internalKonventionExtension =
            extensions.findByName(InternalKonventionExtension.NAME) as InternalKonventionExtension?
                ?: extensions.create(InternalKonventionExtension.NAME, InternalKonventionExtension::class.java)
        private val javaVersion = internalProperties.getVersion("javaVersion")
        private val kotlinVersion = internalProperties.getVersion("kotlinVersion")
        private val jvmVendor = JvmVendorSpec.ADOPTIUM

        fun run() {
            configureRepositories()
            pluginManager.apply(internalProperties.getPlugin("kotlin-jvm").get().pluginId)
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
            tasks.withType(KotlinCompile::class.java) { kotlinCompile ->
                kotlinCompile.compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion))
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
        }

        fun configureRepositories() {
            if (internalEnvironment.isLocal()) {
                repositories.add(repositories.mavenLocal())
            }
            repositories.add(repositories.mavenCentral())
        }

        fun configureKotlin() {
            extensions.configure<KotlinJvmProjectExtension>("kotlin") { kotlin ->
                kotlin.jvmToolchain { jvmToolchain ->
                    jvmToolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
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
