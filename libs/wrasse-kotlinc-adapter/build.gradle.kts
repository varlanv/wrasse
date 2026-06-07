plugins {
    alias(libs.plugins.internalConvention)
}

description = "Wrasse adapter — LightTree to WNode translation. Depends on kotlinc."

dependencies {
    implementation(projects.libs.wrasseModel)
    compileOnly(libs.kotlin.compiler.embeddable)
}
