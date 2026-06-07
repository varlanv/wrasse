plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    implementation(projects.testing.wrasseTestHarness)
    implementation(projects.testing.commonTest)
    implementation(projects.app.wrasseKotlincPlugin)
}
