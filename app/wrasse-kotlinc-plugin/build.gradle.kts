plugins {
    alias(libs.plugins.internalConvention)
    alias(libs.plugins.shadow)
    `maven-publish`
}

description = "Wrasse compiler plugin — the published JAR users add to kotlinCompilerPluginClasspath."

dependencies {
    implementation(libs.koper.lang)
    implementation(projects.libs.wrasseModel)
    implementation(projects.libs.wrasseKotlincAdapter)
    implementation(projects.libs.wrasseRules)
    implementation(projects.libs.wrasseFormat)
    implementation(projects.libs.wrasseLang)
    runtimeOnly(projects.app.wrasseKotlincInternalK20)
    runtimeOnly(projects.app.wrasseKotlincInternalK22)
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.reflect)
    testImplementation(libs.kotlin.compiler.embeddable)
}

shadow {
    addShadowVariantIntoJavaComponent = false
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.varlanv.koper", "com.varlanv.wrasse.internal.shaded.koper")
    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.varlanv.wrasse"
            artifactId = "compiler-plugin"
            version = project.version.toString()
            from(components["shadow"])
        }
    }
}
