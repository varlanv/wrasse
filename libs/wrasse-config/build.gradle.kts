plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse config model"

dependencies {
    implementation(projects.libs.wrasseLang)
}
