package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * One compilation's patch file, kept as an append-only journal: [record] and [clear] append one
 * block each instead of rewriting the file, and both are no-ops when nothing changes. The journal
 * is loaded on first use and compacted (rewritten atomically without superseded blocks) at that
 * point, and again whenever the superseded blocks outnumber the live entries, so the file stays
 * small and every reader still sees exactly one entry per path through [WPatchReader]. A
 * compilation that records nothing still leaves a header-only file behind, as before.
 */
class WPatchStore(private val dir: Path) {
    private val lock = Any()
    private var entries: LinkedHashMap<String, FileEdits>? = null
    private var redundantBlocks = 0
    private var journalOnDisk = false

    fun record(entry: FileEdits) {
        synchronized(lock) {
            val live = loaded()
            val previous = live[entry.filePath]
            if (previous != null && previous.sameContent(entry)) return
            live[entry.filePath] = entry
            if (previous != null) redundantBlocks++
            if (!compactIfBloated(live)) appendBlock { WPatchWriter.write(it, entry) }
        }
    }

    fun clear(filePath: String) {
        synchronized(lock) {
            val live = loaded()
            if (live.remove(filePath) == null) return
            redundantBlocks += 2
            if (!compactIfBloated(live)) appendBlock { WPatchWriter.writeTombstone(it, filePath) }
        }
    }

    private fun loaded(): LinkedHashMap<String, FileEdits> {
        entries?.let { return it }
        val text = try {
            Files.readString(patchFile())
        } catch (_: NoSuchFileException) {
            null
        }
        val live = LinkedHashMap<String, FileEdits>()
        if (text != null) {
            journalOnDisk = true
            val journal = WPatchReader.readJournal(text)
            for (entry in journal.entries) live[entry.filePath] = entry
            redundantBlocks = journal.blockCount - journal.entries.size
        }
        entries = live
        if (text == null || redundantBlocks > 0) rewriteCompacted(live)
        return live
    }

    private fun compactIfBloated(live: Map<String, FileEdits>): Boolean {
        if (redundantBlocks <= maxOf(MIN_REDUNDANT_BLOCKS, live.size)) return false
        rewriteCompacted(live)
        return true
    }

    private fun rewriteCompacted(live: Map<String, FileEdits>) {
        Files.createDirectories(dir)
        val patchFile = patchFile()
        val tmpFile = dir.resolve("$PATCH_FILE_NAME.tmp")
        Files.newBufferedWriter(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
            WPatchWriter.writeAll(out, live.values)
        }
        Files.move(tmpFile, patchFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        redundantBlocks = 0
        journalOnDisk = true
    }

    private inline fun appendBlock(write: (Appendable) -> Unit) {
        if (!journalOnDisk) Files.createDirectories(dir)
        Files.newBufferedWriter(patchFile(), StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { out ->
            if (!journalOnDisk) WPatchWriter.writeHeader(out)
            write(out)
        }
        journalOnDisk = true
    }

    fun patchFile(): Path = dir.resolve(PATCH_FILE_NAME)

    companion object {
        const val PATCH_FILE_NAME = "wrasse-fixes.txt"

        /** The subdirectory of a compilation's `fixOutputDir` that holds nothing but the journal, so a build tool can declare it as the compile's output. */
        const val PATCH_DIR_NAME = "patch"
        private const val MIN_REDUNDANT_BLOCKS = 64
    }
}
