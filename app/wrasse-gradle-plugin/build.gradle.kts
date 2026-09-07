import kotlin.jvm.optionals.getOrNull

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

description = "Wrasse Gradle plugin — wires the compiler plugin into every Kotlin compile and adds wrasseLint/wrasseFormat/wrasseApply."

val catalog = versionCatalogs.named("libs")
val javaToolchainVersion = catalog.findVersion("javaToolchainVersion").getOrNull()!!.requiredVersion
val javaTargetVersion = catalog.findVersion("javaTargetVersion").getOrNull()!!.requiredVersion
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

val generateWrasseVersion by tasks.registering {
    val version = project.version.toString()
    val outDir = layout.buildDirectory.dir("generated/wrasse-version")
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/varlanv/wrasse/gradle/WrasseVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.varlanv.wrasse.gradle\n\ninternal object WrasseVersion {\n    const val VALUE = \"$version\"\n}\n")
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(generateWrasseVersion) }

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.kotest.assertions)
    testImplementation(libs.kotlin.kotest.junit5Runner)
}

gradlePlugin {
    plugins {
        create("wrasse") {
            id = "com.varlanv.wrasse"
            implementationClass = "com.varlanv.wrasse.gradle.WrasseGradlePlugin"
            displayName = "Wrasse"
            description = project.description
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
        showStackTraces = true
    }
    systemProperty("wrasse.version", project.version.toString())
    systemProperty("wrasse.kotlinVersion", kotlinVersion)
    systemProperty("wrasse.javaToolchainVersion", javaToolchainVersion)
    dependsOn(
        ":libs:wrasse-lang:publishToMavenLocal",
        ":libs:wrasse-model:publishToMavenLocal",
        ":libs:wrasse-rules:publishToMavenLocal",
        ":libs:wrasse-format:publishToMavenLocal",
        ":libs:wrasse-kotlinc-adapter:publishToMavenLocal",
        ":app:wrasse-kotlinc-internal-k20:publishToMavenLocal",
        ":app:wrasse-kotlinc-internal-k22:publishToMavenLocal",
        ":app:wrasse-kotlinc-plugin:publishToMavenLocal",
    )
}
