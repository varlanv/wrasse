plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse formatting engine"

dependencies {
    implementation(projects.libs.wrasseModel)
}
