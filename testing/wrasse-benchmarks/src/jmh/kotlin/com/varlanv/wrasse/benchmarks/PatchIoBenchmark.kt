package com.varlanv.wrasse.benchmarks

import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.Sha256
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.lang.WPatchStore
import com.varlanv.wrasse.lang.WPatchWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

private const val FILE_COUNT = 400
private const val EDITS_PER_FILE = 4

@State(Scope.Benchmark)
open class PatchIoState {
    lateinit var dir: Path
    lateinit var entries: List<FileEdits>
    lateinit var sources: List<Path>

    @Setup(Level.Invocation)
    fun setUp() {
        dir = Files.createTempDirectory("wrasse-patch-bench-")
        val corpus = BenchmarkCorpusGenerator.generate(FILE_COUNT)
        sources = corpus.files.mapIndexed { i, file ->
            val path = dir.resolve("src").resolve("F$i.kt")
            Files.createDirectories(path.parent)
            Files.writeString(path, file.content)
            path
        }
        entries = corpus.files.mapIndexed { i, file ->
            val step = file.content.length / (EDITS_PER_FILE + 1)
            val edits = (1..EDITS_PER_FILE).map { k -> WEdit(k * step, k * step, "/*e$k*/") }
            FileEdits(sources[i].toString(), Sha256.ofText(file.content), edits)
        }
    }

    @TearDown(Level.Invocation)
    fun tearDown() {
        Files.walk(dir).use { walk -> walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }
}

open class PatchIoBenchmark {

    @Benchmark
    fun storeRewriteWholeFilePerChange(state: PatchIoState, blackhole: Blackhole) {
        val patchDir = state.dir.resolve("rewrite")
        var current: List<FileEdits> = emptyList()
        for (entry in state.entries) {
            current = current + entry
            Files.createDirectories(patchDir)
            val tmp = patchDir.resolve("wrasse-fixes.txt.tmp")
            Files.newBufferedWriter(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { WPatchWriter.writeAll(it, current) }
            Files.move(tmp, patchDir.resolve("wrasse-fixes.txt"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        blackhole.consume(current.size)
    }

    @Benchmark
    fun storeJournal(state: PatchIoState, blackhole: Blackhole) {
        val store = WPatchStore(state.dir.resolve("journal"))
        for (entry in state.entries) store.record(entry)
        blackhole.consume(store)
    }

    @Benchmark
    fun applyCopyingWholeContent(state: PatchIoState, blackhole: Blackhole) {
        var applied = 0
        for (entry in state.entries) {
            val path = Path.of(entry.filePath)
            val content = Files.readString(path)
            if (Sha256.ofText(content) != entry.sourceHash) continue
            val sb = StringBuilder(content)
            for (edit in entry.edits.sortedByDescending { it.startOffset }) sb.replace(edit.startOffset, edit.endOffset, edit.replacement)
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            Files.writeString(tmp, sb.toString())
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            applied++
        }
        blackhole.consume(applied)
    }

    @Benchmark
    fun applyStreaming(state: PatchIoState, blackhole: Blackhole) {
        val patchDir = state.dir.resolve("apply")
        Files.createDirectories(patchDir)
        Files.newBufferedWriter(patchDir.resolve("wrasse-fixes.txt")).use { WPatchWriter.writeAll(it, state.entries) }
        blackhole.consume(WPatchApplier.apply(patchDir).files.size)
    }
}
