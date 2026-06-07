plugins {
    alias(libs.plugins.internalConvention)
    `maven-publish`
}

description = "Wrasse compiler plugin — the published JAR users add to kotlinCompilerPluginClasspath."

dependencies {
    implementation(projects.libs.wrasseModel)
    implementation(projects.libs.wrasseConfig)
    implementation(projects.libs.wrasseKotlincAdapter)
    implementation(projects.libs.wrasseRules)
    implementation(projects.libs.wrasseFormat)
    implementation(projects.libs.wrasseLang)
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.reflect)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.varlanv.wrasse"
            artifactId = "compiler-plugin"
            version = project.version.toString()
            from(components["java"])
        }
    }
}
