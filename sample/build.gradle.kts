plugins {
    alias(libs.plugins.internalConvention)
    application
}

description = "Wrasse sample project"

application {
    mainClass.set("com.varlanv.wrasse.sample.MainKt")
}

dependencies {
    kotlinCompilerPluginClasspath(project(":compiler-plugin"))
}
