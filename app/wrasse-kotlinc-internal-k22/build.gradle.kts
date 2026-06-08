plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
    compileOnly(projects.app.wrasseKotlincPlugin) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
    }
    compileOnly(projects.libs.wrasseConfig)
}

configurations.named("compileClasspath") {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
    }
}
