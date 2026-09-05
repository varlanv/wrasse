plugins {
    alias(libs.plugins.versionCatalogPlugin)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

versionCatalogUpdate {
    keep {
        keepUnusedVersions = true
    }
}

fun Project.forkGradle(
    vararg args: String,
    failOnError: Boolean = true,
) {
    val osName = providers.systemProperty("os.name").get()
    val gradlew = file(
        if (osName.lowercase().contains("windows")) "gradlew.bat" else "gradlew"
    ).absolutePath
    val logArgs = args.toMutableList()
    logArgs.addFirst("gradle")
    logArgs.addLast("--no-daemon")
    logger.lifecycle("Forking gradle task: [ ${logArgs.joinToString(" ")} ]")
    val result = providers.exec {
        commandLine(gradlew, "--no-daemon", *args)
        isIgnoreExitValue = true
    }
    val stdout = result.standardOutput.asText.get()
    val stderr = result.standardError.asText.get()
    if (stdout.isNotBlank()) println(stdout)
    if (stderr.isNotBlank()) System.err.println(stderr)
    if (failOnError && result.result.get().exitValue != 0) {
        throw GradleException("Forked gradle task failed")
    }
}

tasks.register("wrasseLint") {
    group = "verification"
    description = "Lint all project sources with the wrasse compiler plugin"
    val republish = providers.gradleProperty("republish")
    doLast {
        if (republish.isPresent) {
            forkGradle("publishToMavenLocal", "-q")
        }
        forkGradle("compileKotlin", "compileTestKotlin", "-PwrasseCheck")
    }
}

tasks.register("wrasseFix") {
    group = "verification"
    description = "Autofix wrasse violations in project sources"
    val republish = providers.gradleProperty("republish")
    doLast {
        if (republish.isPresent) {
            forkGradle("publishToMavenLocal", "-q")
        }
        forkGradle("wrasseFormatRequest", "-q")
        forkGradle("compileKotlin", "compileTestKotlin", "-PwrasseCheck", failOnError = false)
        forkGradle("wrasseApply", failOnError = true)
    }
}

tasks.register("testMinorHarness") {
    group = "verification"
    description = "Run fixture tests against all supported Kotlin minor versions"
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-1-x:testMinor")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-2-x:testMinor")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-3-x:testMinor")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-4-x:testMinor")
}

tasks.register("testPatchHarness") {
    group = "verification"
    description = "Run fixture tests against all Kotlin patch versions"
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-1-x:testPatchHarness")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-2-x:testPatchHarness")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-3-x:testPatchHarness")
    dependsOn(":testing:wrasse-kotlinc-plugin-tests-2-4-x:testPatchHarness")
}
