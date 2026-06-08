plugins {
    alias(libs.plugins.internalConvention)
}

dependencies {
    implementation(projects.testing.wrasseTestHarness)
    implementation(projects.testing.commonTest)
    implementation(projects.app.wrasseKotlincPlugin)
    runtimeOnly(projects.app.wrasseKotlincInternalK20)
    runtimeOnly(projects.app.wrasseKotlincInternalK22)
}

val fixturesDir = project(":testing:wrasse-test-harness")
    .file("src/main/resources/fixtures").absolutePath

tasks.withType<Test>().configureEach {
    systemProperty("wrasse.fixtures.dir", fixturesDir)
}

val kotlinMinorVersions = listOf("2.1.21", "2.2.21", "2.3.21")

for (version in kotlinMinorVersions) {
    val safeName = version.replace(".", "_")
    val kotlincConfig = configurations.create("kotlinc_$safeName") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

    dependencies {
        kotlincConfig("org.jetbrains.kotlin:kotlin-compiler-embeddable:$version")
    }

    tasks.register<Test>("testKotlin_$safeName") {
        group = "verification"
        description = "Run fixture tests against Kotlin $version"
        testClassesDirs = tasks.named<Test>("test").get().testClassesDirs
        systemProperty("wrasse.fixtures.dir", fixturesDir)

        val baseClasspath = tasks.named<Test>("test").get().classpath
        classpath = files(kotlincConfig) + baseClasspath.filter {
            !it.name.startsWith("kotlin-compiler-embeddable")
        }
    }
}

tasks.register("testMinors") {
    group = "verification"
    description = "Run fixture tests against all supported Kotlin minor versions"
    dependsOn(tasks.named("test"))
    for (version in kotlinMinorVersions) {
        dependsOn(tasks.named("testKotlin_${version.replace(".", "_")}"))
    }
}
