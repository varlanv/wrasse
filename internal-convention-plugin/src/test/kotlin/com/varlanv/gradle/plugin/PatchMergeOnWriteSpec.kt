package com.varlanv.gradle.plugin

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end coverage of D22 at the real Gradle wiring level: per-compile-task patch directories
 * (`build/wrasse/main`, `build/wrasse/test`) and merge-on-write emission, driven through the real
 * `internal-gradle-convention-plugin` and a real Kotlin incremental compile — the thing
 * [com.varlanv.wrasse.lang.WPatchMergeSpec] (pure logic, no Gradle/kotlinc involved) cannot cover.
 *
 * The incremental scenario configures the rule at `warn` level and disables
 * `allWarningsAsErrors` for the fixture module: this repo's own convention wiring always sets
 * `allWarningsAsErrors = true`, which turns any wrasse diagnostic (`warn` or `error`) into a
 * `-Werror` compile failure. Kotlin's Gradle incremental compiler only computes a precise
 * per-file "dirty set" (`SourcesChanges.Known`) relative to the last *successful* compile — after
 * a failed compile it falls back to reprocessing every source (`SourcesChanges.Unknown`), which
 * would make the untouched sibling file's entry survive incidentally (full reprocess) rather than
 * because merge-on-write preserved it. Letting one round succeed with warnings is what actually
 * exercises the untouched-file-preservation path this test is named for.
 */
private val catalogPath: String =
    (System.getProperty("wrasse.realRepoCatalogPath") ?: error("system property 'wrasse.realRepoCatalogPath' not set"))
        .replace("\\", "\\\\")

private fun writeFixtureProject(projectDir: Path, ruleLevel: String, allowWarnings: Boolean): Path {
    Files.writeString(
        projectDir.resolve("settings.gradle.kts"),
        """
        pluginManagement {
            repositories {
                gradlePluginPortal()
            }
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
            }
        }
        plugins {
            id("org.gradle.toolchains.foojay-resolver-convention")
        }
        dependencyResolutionManagement {
            versionCatalogs {
                create("libs") {
                    from(files("$catalogPath"))
                }
            }
        }
        include("module", "testing:common-test")
        """.trimIndent(),
    )
    Files.writeString(projectDir.resolve("build.gradle.kts"), "")
    Files.createDirectories(projectDir.resolve("testing/common-test"))
    Files.writeString(
        projectDir.resolve("testing/common-test/build.gradle.kts"),
        """
        plugins {
            id("com.varlanv.wrasse.gradle.internal-gradle-convention-plugin")
        }
        """.trimIndent(),
    )

    val moduleDir = Files.createDirectories(projectDir.resolve("module"))
    val allowWarningsOverride = if (allowWarnings) {
        """

        afterEvaluate {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions {
                    allWarningsAsErrors.set(false)
                }
            }
        }
        """.trimIndent()
    } else {
        ""
    }
    Files.writeString(
        moduleDir.resolve("build.gradle.kts"),
        """
        plugins {
            id("com.varlanv.wrasse.gradle.internal-gradle-convention-plugin")
        }
        $allowWarningsOverride
        """.trimIndent(),
    )
    Files.writeString(
        moduleDir.resolve("wrasse.json"),
        """{"rules":{"no-semicolons":{"level":"$ruleLevel"}}}""",
    )
    return moduleDir
}

private fun writeSource(moduleDir: Path, relativePath: String, content: String): Path {
    val file = moduleDir.resolve(relativePath)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
    return file.toAbsolutePath().normalize()
}

private fun runExpectingFailure(projectDir: Path, vararg args: String): BuildResult =
    GradleRunner.create()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withArguments(*args, "--stacktrace")
        .buildAndFail()

private fun runExpectingSuccess(projectDir: Path, vararg args: String): BuildResult =
    GradleRunner.create()
        .withPluginClasspath()
        .withProjectDir(projectDir.toFile())
        .withArguments(*args, "--stacktrace")
        .build()

private fun readPatch(moduleDir: Path, compilation: String): String {
    val patchFile = moduleDir.resolve("build/wrasse/$compilation/wrasse-fixes.txt")
    check(Files.exists(patchFile)) { "expected a patch file at $patchFile" }
    return Files.readString(patchFile)
}

private fun filePathsIn(patchContent: String): Set<String> =
    patchContent.lineSequence().filter { it.startsWith("file:") }.map { it.removePrefix("file:") }.toSet()

private fun editCountFor(patchContent: String, filePath: String): Int {
    val lines = patchContent.lines()
    val fileLineIndex = lines.indexOf("file:$filePath")
    check(fileLineIndex >= 0) { "file:$filePath not found in patch content:\n$patchContent" }
    var count = 0
    var i = fileLineIndex + 1
    while (i < lines.size && !lines[i].startsWith("file:")) {
        if (lines[i].startsWith("edit:")) count++
        i++
    }
    return count
}

private fun hashFor(patchContent: String, filePath: String): String {
    val lines = patchContent.lines()
    val fileLineIndex = lines.indexOf("file:$filePath")
    check(fileLineIndex >= 0) { "file:$filePath not found in patch content:\n$patchContent" }
    val hashLine = lines.getOrNull(fileLineIndex + 1) ?: error("no hash line after file:$filePath")
    check(hashLine.startsWith("hash:")) { "expected a hash line after file:$filePath, got: $hashLine" }
    return hashLine.removePrefix("hash:")
}

class PatchMergeOnWriteSpec : ShouldSpec({

    should(
        "write main and test compile tasks to distinct per-compilation patch directories, each holding only " +
            "its own files' entries, with emission always-on even for the clean main compile (D22 point 1)"
    ) {
        val projectDir = Files.createTempDirectory("patch-merge-separate-compilations-")
        try {
            val moduleDir = writeFixtureProject(projectDir, ruleLevel = "error", allowWarnings = false)
            writeSource(moduleDir, "src/main/kotlin/Main.kt", "package sample\n\nclass Main {\n    val x = 1\n}\n")
            val testFile = writeSource(moduleDir, "src/test/kotlin/MainTest.kt", "package sample\n\nclass MainTest {\n    val y = 2;\n}\n")

            runExpectingFailure(projectDir, ":module:compileKotlin", ":module:compileTestKotlin", "-PwrasseCheck")

            val mainPatch = readPatch(moduleDir, "main")
            val testPatch = readPatch(moduleDir, "test")
            filePathsIn(mainPatch) shouldBe emptySet()
            filePathsIn(testPatch) shouldBe setOf(testFile.toString())
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    should(
        "preserve an untouched file's patch entry across a true incremental recompile that only reprocesses a " +
            "sibling file, while updating the changed file's entry (D22 merge-on-write; a truncate-per-compile " +
            "write loses the untouched file's entry in this exact scenario — verified separately by reverting " +
            "WrassePlugin's merge-on-write to the old truncate/append write and observing this scenario fail)"
    ) {
        val projectDir = Files.createTempDirectory("patch-merge-incremental-")
        try {
            val moduleDir = writeFixtureProject(projectDir, ruleLevel = "warn", allowWarnings = true)
            val aFile = writeSource(moduleDir, "src/main/kotlin/A.kt", "package sample\n\nclass A {\n    val x = 1;\n}\n")
            val bFile = writeSource(moduleDir, "src/main/kotlin/B.kt", "package sample\n\nclass B {\n    val y = 2;\n}\n")

            runExpectingSuccess(projectDir, ":module:compileKotlin", "-PwrasseCheck")

            val fullCheckPatch = readPatch(moduleDir, "main")
            filePathsIn(fullCheckPatch) shouldBe setOf(aFile.toString(), bFile.toString())
            editCountFor(fullCheckPatch, aFile.toString()) shouldBe 1
            editCountFor(fullCheckPatch, bFile.toString()) shouldBe 1
            val bHashBeforeIncrementalRound = hashFor(fullCheckPatch, bFile.toString())

            writeSource(moduleDir, "src/main/kotlin/A.kt", "package sample\n\nclass A {\n    val x = 11;\n}\n")

            runExpectingSuccess(projectDir, ":module:compileKotlin", "-PwrasseCheck")

            val incrementalPatch = readPatch(moduleDir, "main")
            filePathsIn(incrementalPatch) shouldBe setOf(aFile.toString(), bFile.toString())
            editCountFor(incrementalPatch, aFile.toString()) shouldBe 1
            editCountFor(incrementalPatch, bFile.toString()) shouldBe 1
            hashFor(incrementalPatch, bFile.toString()) shouldBe bHashBeforeIncrementalRound

            runExpectingSuccess(projectDir, ":module:wrasseApply")

            Files.readString(aFile) shouldBe "package sample\n\nclass A {\n    val x = 11\n}\n"
            Files.readString(bFile) shouldBe "package sample\n\nclass B {\n    val y = 2\n}\n"

            runExpectingSuccess(projectDir, ":module:compileKotlin", "-PwrasseCheck")

            val postFixPatch = readPatch(moduleDir, "main")
            filePathsIn(postFixPatch) shouldBe emptySet()
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }
})
