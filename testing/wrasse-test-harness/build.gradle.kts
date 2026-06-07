plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    api(projects.testing.commonTest)
    api(libs.kotlin.compiler.embeddable)
    implementation(projects.app.wrasseKotlincPlugin)
}
