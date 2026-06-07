plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse compiler plugin"

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.reflect)
}
