package com.varlanv.wrasse.bench

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

/**
 * Writes one standalone Gradle project per requested size under `<outDir>/<size>`: the generated
 * sources twice (`src/` to work on, `src-pristine/` to restore from), one deliberately broken copy
 * of the first file under `src-broken/`, the build files from `bench-template`, and the Gradle
 * wrapper copied from `<repoRoot>` so every project runs the same Gradle. Nothing here is
 * committed; `bench.sh` regenerates on demand.
 */
fun main(args: Array<String>) {
    require(args.size == 4) { "usage: <outDir> <repoRoot> <sizes-csv> <jdk-home>" }
    val outDir = Path.of(args[0])
    val repoRoot = Path.of(args[1])
    val jdkHome = args[3]
    for (name in args[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
        generateProject(outDir.resolve(name), repoRoot, Sizes.byName(name), jdkHome)
    }
}

private fun generateProject(
    dir: Path,
    repoRoot: Path,
    spec: SizeSpec,
    jdkHome: String,
) {
    if (dir.exists()) deleteRecursively(dir)
    dir.createDirectories()

    val stress = StressFiles.generate(spec.stress)
    val stressLines = stress.sumOf { it.content.count { c -> c == '\n' } }
    val seed = 20_260_905L + spec.targetLines
    val probe = KotlinSourceGen(seed = seed, fileCount = PROBE_FILES)
    val averageLines = (0 until PROBE_FILES).sumOf { probe.generate(it).content.count { c -> c == '\n' } } / PROBE_FILES
    val regularFiles = maxOf(2, (spec.targetLines - stressLines) / averageLines)
    val gen = KotlinSourceGen(seed = seed, fileCount = regularFiles)

    var lines = 0L
    var files = 0
    var firstFile: KotlinSourceGen.GeneratedFile? = null

    fun write(file: KotlinSourceGen.GeneratedFile) {
        for (root in listOf("src", "src-pristine")) {
            val target = dir.resolve(file.relativePath.replaceFirst("src/", "$root/"))
            target.parent.createDirectories()
            target.writeText(file.content)
        }
        lines += file.content.count { it == '\n' } + 1
        files++
    }
    for (index in 0 until regularFiles) {
        val file = gen.generate(index)
        if (firstFile == null) firstFile = file
        write(file)
    }
    for (file in stress) write(file)

    val broken = firstFile ?: error("no files generated")
    val brokenTarget = dir.resolve("src-broken").resolve(broken.relativePath.removePrefix("src/main/kotlin/"))
    brokenTarget.parent.createDirectories()
    brokenTarget.writeText(
        broken.content.trimEnd('\n') + "\n\nfun    brokenLater( a:Int,b : Int ) : Int { return a+b ; }\n",
    )

    writeTemplates(dir, spec, broken.relativePath, jdkHome)
    copyWrapper(repoRoot, dir)
    println("bench project '${spec.name}': $files files, $lines lines at $dir")
}

private fun writeTemplates(
    dir: Path,
    spec: SizeSpec,
    brokenRelativePath: String,
    jdkHome: String,
) {
    val loader = BenchTemplates::class.java.classLoader
    val templates = mapOf(
        "settings.gradle.kts" to "settings.gradle.kts",
        "build.gradle.kts" to "build.gradle.kts",
        "gradle.properties" to "gradle.properties",
        "wrasse.json" to "wrasse.json",
        "editorconfig" to ".editorconfig",
        "gitignore" to ".gitignore",
    )
    for ((name, target) in templates) {
        val text = loader
            .getResourceAsStream("bench-template/$name")
            ?.bufferedReader()
            ?.readText() ?: error("missing template $name")
        dir
            .resolve(target)
            .writeText(
                text
                    .replace("@ROOT_NAME@", "wrasse-bench-${spec.name}")
                    .replace("@BROKEN_FILE@", brokenRelativePath)
                    .replace("@JDK_HOME@", jdkHome),
            )
    }
}

private fun copyWrapper(repoRoot: Path, dir: Path) {
    for (relative in listOf(
        "gradlew",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties",
    )) {
        val source = repoRoot.resolve(relative)
        val target = dir.resolve(relative)
        target.parent.createDirectories()
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
    }
    dir.resolve("gradlew").toFile().setExecutable(true)
}

private fun deleteRecursively(path: Path) {
    if (path.isDirectory()) Files.list(path).use { children -> children.forEach { deleteRecursively(it) } }
    Files.delete(path)
}

private object BenchTemplates

private const val PROBE_FILES = 40
