plugins {
    alias(libs.plugins.versionCatalogPlugin)
    alias(libs.plugins.wrasse) apply false
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

subprojects {
    if (path != ":testing:wrasse-kotlinc-plugin-tests") {
        pluginManager.apply("com.varlanv.wrasse")
    }
}
