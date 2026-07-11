plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse rules implementation"

dependencies {
    implementation(projects.libs.wrasseModel)
}
