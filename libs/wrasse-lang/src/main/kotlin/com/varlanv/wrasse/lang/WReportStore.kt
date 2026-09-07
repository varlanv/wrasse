package com.varlanv.wrasse.lang

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * One compilation's diagnostics report, next to its patch journal and kept the same way: an
 * append-only journal of [ReportedFile] blocks, one live entry per path, compacted on first load
 * and whenever superseded blocks outnumber the live entries. [record] and [clear] are no-ops when
 * the file's entry would not change.
 */
class WReportStore(private val dir: Path) {
    private val lock = Any()
    private var entries: LinkedHashMap<String, ReportedFile>? = null
    private var redundantBlocks = 0
    private var journalOnDisk = false

    fun record(entry: ReportedFile) {
        synchronized(lock) {
            val live = loaded()
            val previous = live[entry.filePath]
            if (previous != null && previous.sameContent(entry)) return
            live[entry.filePath] = entry
            if (previous != null) redundantBlocks++
            if (!compactIfBloated(live)) appendBlock { WReportWriter.write(it, entry) }
        }
    }

    fun clear(filePath: String) {
        synchronized(lock) {
            val live = loaded()
            if (live.remove(filePath) == null) return
            redundantBlocks += 2
            if (!compactIfBloated(live)) appendBlock { WReportWriter.writeTombstone(it, filePath) }
        }
    }

    fun reportFile(): Path = dir.resolve(REPORT_FILE_NAME)

    private fun loaded(): LinkedHashMap<String, ReportedFile> {
        entries?.let { return it }
        val text = try {
            Files.readString(reportFile())
        } catch (_: NoSuchFileException) {
            null
        }
        val live = LinkedHashMap<String, ReportedFile>()
        if (text != null) {
            journalOnDisk = true
            val journal = WReportReader.readJournal(text)
            for (entry in journal.entries) live[entry.filePath] = entry
            redundantBlocks = journal.blockCount - journal.entries.size
        }
        entries = live
        if (text == null || redundantBlocks > 0) rewriteCompacted(live)
        return live
    }

    private fun compactIfBloated(live: Map<String, ReportedFile>): Boolean {
        if (redundantBlocks <= maxOf(MIN_REDUNDANT_BLOCKS, live.size)) return false
        rewriteCompacted(live)
        return true
    }

    private fun rewriteCompacted(live: Map<String, ReportedFile>) {
        Files.createDirectories(dir)
        val tmpFile = dir.resolve("$REPORT_FILE_NAME.tmp")
        Files
            .newBufferedWriter(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            .use { out ->
                WReportWriter.writeAll(out, live.values)
            }
        Files.move(tmpFile, reportFile(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        redundantBlocks = 0
        journalOnDisk = true
    }

    private inline fun appendBlock(write: (Appendable) -> Unit) {
        if (!journalOnDisk) Files.createDirectories(dir)
        Files
            .newBufferedWriter(reportFile(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            .use { out ->
                if (!journalOnDisk) WReportWriter.writeHeader(out)
                write(out)
            }
        journalOnDisk = true
    }

    companion object {
        const val REPORT_FILE_NAME = "wrasse-report.txt"
        private const val MIN_REDUNDANT_BLOCKS = 32
    }
}
