plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    api(libs.kotlin.kotest.assertions)
    api(libs.kotlin.kotest.junit5Runner)
}
