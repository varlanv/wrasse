import java.security.MessageDigest
import org.gradle.plugin.compatibility.compatibility
import kotlin.jvm.optionals.getOrNull

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
}

description = "Wrasse Gradle plugin — applies Wrasse formatter&linter tool as kotlin compiler plugin into every Kotlin compile task"

val catalog = versionCatalogs.named("libs")
val javaToolchainVersion = catalog.findVersion("javaToolchainVersion").getOrNull()!!.requiredVersion
val javaTargetVersion = "17"
val kotlinVersion = catalog.findVersion("kotlinVersion").getOrNull()!!.requiredVersion

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    compilerOptions {
        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(
        if (name.contains("Test")) org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaToolchainVersion)
        else org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaTargetVersion),
    )
}
tasks.withType<JavaCompile>().configureEach {
    if (name.contains("Test")) {
        sourceCompatibility = javaToolchainVersion
        targetCompatibility = javaToolchainVersion
    } else {
        sourceCompatibility = javaTargetVersion
        targetCompatibility = javaTargetVersion
    }
}

val compilerPluginJar = configurations.create("compilerPluginJar") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val generateBundledCompilerPlugin = tasks.register("generateBundledCompilerPlugin") {
    val jars = compilerPluginJar
    val outDir = layout.buildDirectory.dir("generated/wrasse-bundled")
    inputs.files(jars)
    outputs.dir(outDir)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256").digest(jars.singleFile.readBytes())
        val sha256 = digest.joinToString("") { byte -> "%02x".format(byte) }
        val file = outDir.get().file("com/varlanv/wrasse/gradle/BundledCompilerPlugin.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.varlanv.wrasse.gradle\n\ninternal object BundledCompilerPlugin {\n    const val SHA256 = \"$sha256\"\n}\n")
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(generateBundledCompilerPlugin) }

tasks.processResources {
    from(compilerPluginJar) {
        rename { "compiler-plugin.jar" }
        into("wrasse")
    }
}

dependencies {
    compilerPluginJar(project(path = ":app:wrasse-kotlinc-plugin", configuration = "shadowRuntimeElements"))
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.kotest.assertions)
    testImplementation(libs.kotlin.kotest.junit5Runner)
}

gradlePlugin {
    website.set("https://github.com/varlanv/wrasse")
    vcsUrl.set("https://github.com/varlanv/wrasse.git")
    plugins {
        create("wrasse") {
            id = "com.varlanv.wrasse"
            implementationClass = "com.varlanv.wrasse.gradle.WrasseGradlePlugin"
            displayName = "Wrasse"
            description = project.description
            tags.set(listOf("kotlin", "lint", "format", "linter", "formatter"))
            compatibility {
                features {
                    configurationCache.set(true)
                    isolatedProjects.set(true)
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        showStackTraces = true
    }
    systemProperty("wrasse.kotlinVersion", kotlinVersion)
    outputs.upToDateWhen { false }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Wrasse")
            description.set(project.description)
            url.set("https://github.com/varlanv/wrasse")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    name.set("Vladyslav Varlan")
                }
            }
            scm {
                url.set("https://github.com/varlanv/wrasse")
                connection.set("scm:git:https://github.com/varlanv/wrasse.git")
            }
        }
    }
}
