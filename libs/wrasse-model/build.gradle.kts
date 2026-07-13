plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse model — WNode, WNodeType, WFile, WRule."

dependencies {
    api(projects.libs.wrasseLang)
}
