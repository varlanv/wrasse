plugins {
    alias(libs.plugins.internalConvention)
}

val latestMinor = "2.2.21"
val patchVersions = listOf("2.2.0", "2.2.10", "2.2.20", "2.2.21")
val isCurrentKotlin = latestMinor == libs.versions.kotlinVersion.get()

dependencies {
    testImplementation(projects.testing.wrasseKotlincPluginTestsBase)
}

if (!isCurrentKotlin) {
    configurations.testRuntimeClasspath {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-compiler-embeddable:$latestMinor")
    }
}

val fixturesDir = rootProject.file("testing/wrasse-test-harness/src/main/resources/fixtures").absolutePath

tasks.withType<Test>().configureEach {
    systemProperty("wrasse.fixtures.dir", fixturesDir)
}

if (!isCurrentKotlin) {
    tasks.test { enabled = false }
}

if (isCurrentKotlin) {
    tasks.register("testMinor") {
        group = "verification"
        description = "Run fixture tests against Kotlin $latestMinor"
        dependsOn(tasks.test)
    }
} else {
    tasks.register<Test>("testMinor") {
        group = "verification"
        description = "Run fixture tests against Kotlin $latestMinor"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
    }
}

for (version in patchVersions) {
    val safeName = version.replace('.', '_')
    val patchConfig = configurations.create("kotlincPatch_$safeName") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies {
        patchConfig("org.jetbrains.kotlin:kotlin-compiler-embeddable:$version")
    }
    tasks.register<Test>("testPatch_$safeName") {
        group = "verification"
        description = "Run fixture tests against Kotlin $version"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = files(patchConfig) + sourceSets["test"].runtimeClasspath.filter {
            !it.name.startsWith("kotlin-compiler-embeddable")
        }
    }
}

tasks.register("testPatchHarness") {
    group = "verification"
    description = "Run fixture tests against all Kotlin 2.2.x patch versions"
    for (version in patchVersions) {
        dependsOn(tasks.named("testPatch_${version.replace('.', '_')}"))
    }
}
