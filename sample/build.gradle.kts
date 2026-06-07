plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

description = "Wrasse sample project"

kotlin {
    jvmToolchain {
        vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.ADOPTIUM)
        languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(libs.versions.javaVersion.get()))
    }
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.varlanv.wrasse.sample.MainKt")
}

dependencies {
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
