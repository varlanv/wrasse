plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse rule implementations. Depends only on wrasse-model."

dependencies {
    api(projects.libs.wrasseModel)
    api(projects.libs.wrasseConfig)
}
