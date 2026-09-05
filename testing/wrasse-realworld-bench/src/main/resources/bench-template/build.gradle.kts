import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.ncorti.ktfmt.gradle") version "0.27.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

val wrassePlugin: Configuration by configurations.creating

dependencies {
    wrassePlugin("com.varlanv.wrasse:compiler-plugin:0.0.1-SNAPSHOT")
}

val wrasseCheck = providers.gradleProperty("wrasseCheck").isPresent
val wrasseFixDir = layout.buildDirectory.dir("wrasse/main").get().asFile.absolutePath

tasks.withType<KotlinCompile>().configureEach {
    if (wrasseCheck) {
        pluginClasspath.from(wrassePlugin)
        compilerOptions.freeCompilerArgs.addAll(
            "-P",
            "plugin:com.varlanv.wrasse:warnOnly=true",
            "-P",
            "plugin:com.varlanv.wrasse:fixOutputDir=$wrasseFixDir",
        )
    }
}

tasks.register<JavaExec>("wrasseApply") {
    group = "wrasse"
    classpath = wrassePlugin
    mainClass.set("com.varlanv.wrasse.lang.WPatchApplierKt")
    args(layout.buildDirectory.dir("wrasse").get().asFile.absolutePath)
}

ktlint {
    version.set("1.8.0")
    ignoreFailures.set(true)
}

ktfmt {
    kotlinLangStyle()
}

detekt {
    buildUponDefaultConfig = true
    ignoreFailures = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
}

tasks.register<Sync>("restoreSources") {
    group = "bench"
    from("src-pristine")
    into("src")
}

tasks.register<Copy>("breakOneFile") {
    group = "bench"
    from("src-broken")
    into("src/main/kotlin")
}
