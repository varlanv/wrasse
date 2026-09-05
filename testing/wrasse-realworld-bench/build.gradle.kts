plugins {
    alias(libs.plugins.internalConvention)
}

description = "Generates synthetic real-world-shaped Gradle projects (5k / 50k / 1M LOC) and runs wrasse, ktlint, ktfmt and detekt against them — see bench.sh; not part of build/test/check"

tasks.register("generateBenchProjects", JavaExec::class.java) {
    group = "benchmark"
    description = "Generates the benchmark projects under bench-projects/<size>"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.varlanv.wrasse.bench.BenchProjectGeneratorKt")
    val jdk21 = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        .map { it.metadata.installationPath.asFile.absolutePath }
    args(
        layout.projectDirectory.dir("bench-projects").asFile.absolutePath,
        rootDir.absolutePath,
        providers.gradleProperty("benchSizes").orElse("5k,50k,1m").get(),
        jdk21.get(),
    )
}
