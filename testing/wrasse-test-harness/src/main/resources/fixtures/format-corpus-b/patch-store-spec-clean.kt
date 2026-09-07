package com.varlanv.wrasse.lang

import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.useTempDir
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class WPatchStoreSpec : BaseSpec({

    fun entry(
        path: String,
        hash: String,
        vararg edits: WEdit,
    ) = FileEdits(path, hash, edits.toList())

    fun journal(dir: Path): String = Files.readString(dir.resolve("wrasse-fixes.txt"))

    fun blocks(dir: Path): Int = journal(dir).lines().count { it.startsWith("file:") }

    should("append one block per change and keep the reader's view at one entry per path") {
        useTempDir { dir ->
            val store = WPatchStore(dir)
            store.record(entry("a.kt", "h1", WEdit(0, 1, "")))
            store.record(entry("b.kt", "h2", WEdit(5, 5, "x")))
            store.record(entry("a.kt", "h3", WEdit(2, 3, "y")))

            journal(dir).startsWith("# wrasse-fixes v1\n") shouldBe true
            blocks(dir) shouldBe 3
            WPatchReader
                .read(journal(dir))
                .map { "${it.filePath}|${it.sourceHash}|${it.edits.single().replacement}" } shouldBe
                listOf("a.kt|h3|y", "b.kt|h2|x")
        }
    }

    should("write a header-only file on first use and not touch it for an identical entry or an absent path") {
        useTempDir { dir ->
            val store = WPatchStore(dir)
            store.clear("nothing.kt")
            journal(dir) shouldBe "# wrasse-fixes v1\n"
            store.record(entry("a.kt", "h1", WEdit(0, 1, "")))
            val after = journal(dir)
            store.record(entry("a.kt", "h1", WEdit(0, 1, "")))
            store.clear("other.kt")
            journal(dir) shouldBe after
        }
    }

    should("clear a path with a tombstone block that the reader honours") {
        useTempDir { dir ->
            val store = WPatchStore(dir)
            store.record(entry("a.kt", "h1", WEdit(0, 1, "")))
            store.clear("a.kt")
            journal(dir) shouldBe "# wrasse-fixes v1\nfile:a.kt\nhash:h1\nedit:0:1:\nfile:a.kt\nhash:-\n"
            WPatchReader.read(journal(dir)) shouldBe emptyList()
        }
    }

    should("compact a journal with superseded blocks when it is first loaded") {
        useTempDir { dir ->
            WPatchStore(dir).let { first ->
                first.record(entry("a.kt", "h1", WEdit(0, 1, "")))
                first.record(entry("a.kt", "h2", WEdit(0, 1, "z")))
                first.record(entry("b.kt", "h3", WEdit(1, 1, "q")))
                first.clear("b.kt")
            }
            blocks(dir) shouldBe 4

            WPatchStore(dir).clear("never-there.kt")

            journal(dir) shouldBe "# wrasse-fixes v1\nfile:a.kt\nhash:h2\nedit:0:1:z\n"
        }
    }

    should("compact once superseded blocks outnumber live entries, without losing any entry") {
        useTempDir { dir ->
            val store = WPatchStore(dir)
            store.record(entry("live.kt", "h", WEdit(0, 0, "k")))
            for (round in 0 until 66) store.record(entry("churn.kt", "h$round", WEdit(round, round, "v$round")))

            blocks(dir) shouldBe 2
            WPatchReader
                .read(journal(dir))
                .map { "${it.filePath}|${it.sourceHash}" } shouldBe listOf("live.kt|h", "churn.kt|h65")
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
