plugins {
    alias(libs.plugins.internalConvention)
}

description = "Fixture and safety tests of the wrasse kotlinc plugin"

dependencies {
    testImplementation(projects.testing.wrasseTestHarness)
    testImplementation(projects.app.wrasseKotlincPlugin)
    testImplementation(projects.libs.wrasseModel)
    testImplementation(projects.libs.wrasseKotlincAdapter)
    testImplementation(projects.libs.wrasseLang)
    testImplementation(libs.kotlin.compiler.embeddable)
}
