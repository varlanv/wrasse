plugins {
    alias(libs.plugins.internalConvention)
}

description = "Base fixture test class for wrasse kotlinc plugin — submodules extend for each Kotlin minor"

dependencies {
    api(projects.testing.commonTest)
    implementation(projects.testing.wrasseTestHarness)
    implementation(projects.app.wrasseKotlincPlugin)
    implementation(projects.libs.wrasseModel)
    implementation(projects.libs.wrasseKotlincAdapter)
    implementation(projects.libs.wrasseLang)
    implementation(libs.kotlin.compiler.embeddable)
    runtimeOnly(projects.app.wrasseKotlincInternalK20)
    runtimeOnly(projects.app.wrasseKotlincInternalK22)
}
