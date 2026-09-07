package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException

private val symlinksSupported: Boolean by lazy {
    try {
        val probe = Files.createTempDirectory("wrasse-symlink-probe-")
        try {
            val target = Files.createDirectory(probe.resolve("target"))
            Files.createSymbolicLink(probe.resolve("link"), target)
            true
        } finally {
            probe.toFile().deleteRecursively()
        }
    } catch (_: AccessDeniedException) {
        false
    }
}

class FileWalkUpSpec : BaseSpec({
    context("findNamed") {
        should("find file in start directory") {
            useTempDir { root ->
                val file = Files.createFile(root.resolve("wrasse.json"))
                FileWalkUp.findNamed(root, "wrasse.json").getOrThrow() shouldBe file.toRealPath()
            }
        }

        should("find file in parent directory") {
            useTempDir { root ->
                val file = Files.createFile(root.resolve("wrasse.json"))
                val child = Files.createDirectory(root.resolve("sub"))
                FileWalkUp.findNamed(child, "wrasse.json").getOrThrow() shouldBe file.toRealPath()
            }
        }

        should("find file in grandparent directory") {
            useTempDir { root ->
                val file = Files.createFile(root.resolve("wrasse.json"))
                val child = Files.createDirectories(root.resolve("a/b"))
                FileWalkUp.findNamed(child, "wrasse.json").getOrThrow() shouldBe file.toRealPath()
            }
        }

        should("return nearest match when file exists at multiple levels") {
            useTempDir { root ->
                Files.createFile(root.resolve("wrasse.json"))
                val child = Files.createDirectory(root.resolve("sub"))
                val nearer = Files.createFile(child.resolve("wrasse.json"))
                FileWalkUp.findNamed(child, "wrasse.json").getOrThrow() shouldBe nearer.toRealPath()
            }
        }

        should("return null when file does not exist anywhere") {
            useTempDir { root ->
                val child = Files.createDirectories(root.resolve("a/b/c"))
                FileWalkUp.findNamed(child, "wrasse.json").getOrThrow().shouldBeNull()
            }
        }

        should("not match directories with the target name") {
            useTempDir { root ->
                Files.createDirectory(root.resolve("wrasse.json"))
                FileWalkUp.findNamed(root, "wrasse.json").getOrThrow().shouldBeNull()
            }
        }

        should("return failure for nonexistent start directory") {
            useTempDir { root ->
                val noSuchDir = root.resolve("does-not-exist")
                val result = FileWalkUp.findNamed(noSuchDir, "wrasse.json")
                val ex = result.exceptionOrNull()
                ex.shouldBeInstanceOf<NoSuchFileException>()
                ex.message shouldBe noSuchDir.toString()
            }
        }

        should("follow symlinks in start directory").config(enabledIf = { symlinksSupported }) {
            useTempDir { root ->
                val real = Files.createDirectories(root.resolve("real"))
                Files.createFile(real.resolve("wrasse.json"))
                val link = Files.createSymbolicLink(root.resolve("link"), real)
                FileWalkUp.findNamed(link, "wrasse.json").getOrThrow() shouldBe real.resolve("wrasse.json").toRealPath()
            }
        }
    }

    context("find with predicate") {
        should("find file matching predicate in start directory") {
            useTempDir { root ->
                val file = Files.createFile(root.resolve("wrasse.json"))
                FileWalkUp.find(root) { it == "wrasse.json" }.getOrThrow() shouldBe file.toRealPath()
            }
        }

        should("match a file satisfying predicate when multiple candidates exist") {
            useTempDir { root ->
                Files.createFile(root.resolve("other.txt"))
                Files.createFile(root.resolve("wrasse.jsonc"))
                Files.createFile(root.resolve("wrasse.json"))
                val names = setOf("wrasse.jsonc", "wrasse.json")
                val result = FileWalkUp.find(root) { it in names }.getOrThrow()!!
                (result.fileName.toString() in names) shouldBe true
            }
        }

        should("find file in parent directory") {
            useTempDir { root ->
                val file = Files.createFile(root.resolve("wrasse.json"))
                val child = Files.createDirectory(root.resolve("sub"))
                FileWalkUp.find(child) { it == "wrasse.json" }.getOrThrow() shouldBe file.toRealPath()
            }
        }

        should("return null when no file matches predicate") {
            useTempDir { root ->
                Files.createFile(root.resolve("other.txt"))
                FileWalkUp.find(root) { it == "wrasse.json" }.getOrThrow().shouldBeNull()
            }
        }

        should("not match directories") {
            useTempDir { root ->
                Files.createDirectory(root.resolve("wrasse.json"))
                FileWalkUp.find(root) { it == "wrasse.json" }.getOrThrow().shouldBeNull()
            }
        }

        should("return failure for nonexistent start directory") {
            useTempDir { root ->
                val noSuchDir = root.resolve("does-not-exist")
                val result = FileWalkUp.find(noSuchDir) { true }
                val ex = result.exceptionOrNull()
                ex.shouldBeInstanceOf<NoSuchFileException>()
                ex.message shouldBe noSuchDir.toString()
            }
        }
    }
})

// fixture-option: trailing-newline
// fixture-aux-file: aux/Stubs.kt
// fixture-aux-file: aux/Kotest.kt
// fixture-aux-file: aux/KotestCollections.kt
// fixture-aux-file: aux/KotestNulls.kt
// fixture-aux-file: aux/KotestTypes.kt
// fixture-aux-file: aux/KotestAssertions.kt
// fixture-aux-file: aux/KotestThrowables.kt
// fixture-aux-file: aux/lang/ConfigValueJsonc.kt
// fixture-aux-file: aux/lang/FileEdits.kt
// fixture-aux-file: aux/lang/FileWalkUp.kt
// fixture-aux-file: aux/lang/FormatRequest.kt
// fixture-aux-file: aux/lang/HexEncoding.kt
// fixture-aux-file: aux/lang/PerfRecorder.kt
// fixture-aux-file: aux/lang/SafeProperties.kt
// fixture-aux-file: aux/lang/Sha256.kt
// fixture-aux-file: aux/lang/StringSlice.kt
// fixture-aux-file: aux/lang/WEdit.kt
// fixture-aux-file: aux/lang/WPatchApplier.kt
// fixture-aux-file: aux/lang/WPatchReader.kt
// fixture-aux-file: aux/lang/WPatchStore.kt
// fixture-aux-file: aux/lang/WPatchWriter.kt
// fixture-aux-file: aux/lang/WPerf.kt
// fixture-aux-file: aux/lang/WReport.kt
// fixture-aux-file: aux/lang/WReportReplay.kt
// fixture-aux-file: aux/lang/WReportStore.kt
// expect-clean
