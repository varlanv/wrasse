plugins {
    alias(libs.plugins.internalConvention)
}

description = "Base fixture test class for wrasse kotlinc plugin — submodules extend for each Kotlin minor"

dependencies {
    api(projects.testing.commonTest)
    implementation(projects.testing.wrasseTestHarness)
    implementation(projects.app.wrasseKotlincPlugin)
    runtimeOnly(projects.app.wrasseKotlincInternalK20)
    runtimeOnly(projects.app.wrasseKotlincInternalK22)
}
