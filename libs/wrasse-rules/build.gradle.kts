plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse rules implementation"

dependencies {
    api(projects.libs.wrasseModel)
    api(projects.libs.wrasseConfig)
}
