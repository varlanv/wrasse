plugins {
    alias(libs.plugins.internalConvention)
    `maven-publish`
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

description = "Wrasse compiler plugin"

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.reflect)
}
