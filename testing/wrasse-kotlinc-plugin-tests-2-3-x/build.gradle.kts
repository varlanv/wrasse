plugins {
    alias(libs.plugins.internalConvention)
}

val latestMinor = "2.3.21"
val patchVersions = listOf("2.3.0", "2.3.10", "2.3.20", "2.3.21")
val isCurrentKotlin = latestMinor == libs.versions.kotlinVersion.get()

dependencies {
    testImplementation(projects.testing.wrasseKotlincPluginTestsBase)
}

if (!isCurrentKotlin) {
    configurations.testRuntimeClasspath {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-compiler-embeddable:$latestMinor")
    }
}

val targetStdlibConfigs = (patchVersions + latestMinor).distinct().associateWith { version ->
    configurations.create("targetStdlib_${version.replace('.', '_')}") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}
dependencies {
    for ((version, config) in targetStdlibConfigs) {
        add(config.name, "org.jetbrains.kotlin:kotlin-stdlib:$version")
    }
}

fun stdlibJarPath(version: String): String =
    targetStdlibConfigs.getValue(version).filter { it.name.startsWith("kotlin-stdlib-") }.singleFile.absolutePath

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
        doFirst {
            systemProperty("wrasse.harness.stdlibPath", stdlibJarPath(latestMinor))
        }
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
        doFirst {
            systemProperty("wrasse.harness.stdlibPath", stdlibJarPath(version))
        }
    }
}

tasks.register("testPatchHarness") {
    group = "verification"
    description = "Run fixture tests against all Kotlin 2.3.x patch versions"
    for (version in patchVersions) {
        dependsOn(tasks.named("testPatch_${version.replace('.', '_')}"))
    }
}
