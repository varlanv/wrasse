plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    api(projects.testing.commonTest)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.org.json)
    implementation(projects.app.wrasseKotlincPlugin)
}
