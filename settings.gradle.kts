pluginManagement {
    repositories {
        if (providers.environmentVariable("CI").getOrNull() != null) {
            mavenLocal()
        }
        gradlePluginPortal()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention").version(
            providers.gradleProperty("foojayToolchainPluginVersion").get()
        )
    }
    includeBuild("internal-convention-plugin")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "wrasse"

val isCi = providers.environmentVariable("CI").getOrNull()?.let { it != "false" } ?: false

buildCache {
    local {
        isEnabled = !isCi
        isPush = !isCi
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "libs:wrasse-model",
    "libs:wrasse-kotlinc-adapter",
    "libs:wrasse-rules",
    "libs:wrasse-format",
    "libs:wrasse-lang",
    "app:wrasse-kotlinc-plugin",
    "app:wrasse-gradle-plugin",
    "testing:common-test",
    "testing:wrasse-test-harness",
    "testing:wrasse-kotlinc-plugin-tests",
    "testing:wrasse-benchmarks",
    "testing:wrasse-realworld-bench",
)
