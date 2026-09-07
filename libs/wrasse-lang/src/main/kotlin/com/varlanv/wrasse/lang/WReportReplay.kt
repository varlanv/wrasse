package com.varlanv.wrasse.lang

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Replays the diagnostics recorded under a `build/wrasse` tree in the form kotlinc prints them,
 * so a build tool can show them again for a compile that did not run. An entry whose source
 * hash no longer matches the file on disk is stale (the file changed since that compile) and is
 * left out, as is one whose file is gone or unreadable. A stored path is resolved against
 * [collect]'s `projectDir` when given, so a build-cache hit restored under a different checkout
 * still finds its files; an absolute stored path is unaffected (`Path.resolve` on an absolute
 * child returns that child as-is).
 */
object WReportReplay {
    /**
     * As above, but a file that [applyResult] shows was just rewritten is not treated as stale:
     * its still-non-fixable diagnostics are [remap]ped onto the new content and persisted back
     * into that file's report entry under the new hash, so a later plain [collect] (no
     * [applyResult]) already finds them current.
     */
    fun collect(
        wrasseDir: Path,
        projectDir: Path? = null,
        applyResult: ApplyResult? = null,
    ): List<ReportedFile> {
        if (!Files.isDirectory(wrasseDir)) return emptyList()
        val appliedByPath = applyResult?.files
            ?.filterIsInstance<FileApplyResult.Applied>()
            ?.associateBy { it.filePath.toString() } ?: emptyMap()
        val result = ArrayList<ReportedFile>()
        Files.walk(wrasseDir).use { paths ->
            paths
                .filter { it.fileName.toString() == WReportStore.REPORT_FILE_NAME && Files.isRegularFile(it) }
                .sorted()
                .forEach { reportFile ->
                    val text = readIfPresent(reportFile) ?: return@forEach
                    val entries = WReportReader.read(text)
                    if (appliedByPath.isEmpty()) {
                        for (entry in entries) {
                            if (entry.diagnostics.isNotEmpty() && isCurrent(entry, projectDir)) result.add(entry)
                        }
                    } else {
                        result.addAll(reconcile(reportFile, entries, appliedByPath, projectDir))
                    }
                }
        }
        return result
    }

    fun render(
        file: ReportedFile,
        diagnostic: ReportedDiagnostic,
        projectDir: Path? = null,
    ): String {
        val prefix = if (diagnostic.isError) "e" else "w"
        return "$prefix: ${resolvePath(
            file.filePath,
            projectDir,
        ).toUri()}:${diagnostic.line}:${diagnostic.column} wrasse: ${diagnostic.message}"
    }

    /**
     * Maps the non-[ReportedDiagnostic.fixable] entries of [diagnostics] through [appliedEdits]
     * (fixable ones were just fixed and are dropped): an offset at or past an edit's
     * `[startOffset, endOffset)` shifts by `replacementLength - (endOffset - startOffset)`; one
     * that falls inside that span has no matching position in [newContent] and is dropped too.
     * Line and column of a surviving offset are recomputed against [newContent] with the same
     * 1-based convention the compiler plugin records them with.
     */
    fun remap(
        diagnostics: List<ReportedDiagnostic>,
        appliedEdits: List<AppliedEdit>,
        newContent: CharSequence,
    ): List<ReportedDiagnostic> {
        val survivors = diagnostics.filter { !it.fixable }
        if (survivors.isEmpty()) return emptyList()
        val ascending = appliedEdits.sortedBy { it.startOffset }
        val lineIndex = NewlineIndex(newContent)
        val result = ArrayList<ReportedDiagnostic>(survivors.size)
        for (diagnostic in survivors) {
            val mapped = remapOffset(diagnostic.offset, ascending) ?: continue
            result.add(
                ReportedDiagnostic(
                    line = lineIndex.lineOf(mapped),
                    column = lineIndex.columnOf(mapped),
                    offset = mapped,
                    level = diagnostic.level,
                    fixable = false,
                    message = diagnostic.message,
                ),
            )
        }
        return result
    }

    private fun remapOffset(offset: Int, ascendingEdits: List<AppliedEdit>): Int? {
        var shift = 0
        for (edit in ascendingEdits) {
            if (offset < edit.startOffset) break
            if (offset < edit.endOffset) return null
            shift += edit.replacementLength - (edit.endOffset - edit.startOffset)
        }
        return offset + shift
    }

    private fun reconcile(
        reportFile: Path,
        entries: List<ReportedFile>,
        appliedByPath: Map<String, FileApplyResult.Applied>,
        projectDir: Path?,
    ): List<ReportedFile> {
        val store = WReportStore(reportFile.parent)
        val current = ArrayList<ReportedFile>()
        for (entry in entries) {
            val resolvedKey = try {
                resolvePath(entry.filePath, projectDir).toString()
            } catch (_: InvalidPathException) {
                null
            }
            val applied = resolvedKey?.let { appliedByPath[it] }
            if (applied == null) {
                if (entry.diagnostics.isNotEmpty() && isCurrent(entry, projectDir)) current.add(entry)
                continue
            }
            val remapped = remapAfterApply(entry, applied)
            if (remapped != null) {
                store.record(remapped)
                current.add(remapped)
            } else {
                store.clear(entry.filePath)
            }
        }
        return current
    }

    private fun remapAfterApply(entry: ReportedFile, applied: FileApplyResult.Applied): ReportedFile? {
        val content = readIfPresent(applied.filePath) ?: return null
        if (Sha256.ofText(content) != applied.newContentHash) return null
        val remapped = remap(entry.diagnostics, applied.edits, content)
        if (remapped.isEmpty()) return null
        return ReportedFile(entry.filePath, applied.newContentHash, remapped)
    }

    private fun isCurrent(entry: ReportedFile, projectDir: Path?): Boolean {
        val text = readIfPresent(entry.filePath, projectDir) ?: return false
        if (Sha256.ofText(text) == entry.sourceHash) return true
        return Sha256.ofText(text.replace("\r\n", "\n")) == entry.sourceHash
    }

    private fun resolvePath(filePath: String, projectDir: Path?): Path =
        if (projectDir != null) projectDir.resolve(filePath) else Path.of(filePath)

    private fun readIfPresent(filePath: String, projectDir: Path?): String? = try {
        readIfPresent(resolvePath(filePath, projectDir))
    } catch (_: InvalidPathException) {
        null
    }

    private fun readIfPresent(path: Path): String? = try {
        Files.readString(path)
    } catch (_: IOException) {
        null
    } catch (_: UncheckedIOException) {
        null
    }
}

private class NewlineIndex(text: CharSequence) {
    private val starts: IntArray

    init {
        var count = 1
        for (i in 0 until text.length) if (text[i] == '\n') count++
        val array = IntArray(count)
        var line = 1
        for (i in 0 until text.length) if (text[i] == '\n') array[line++] = i + 1
        starts = array
    }

    fun lineOf(offset: Int): Int {
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= offset) low = mid else high = mid - 1
        }
        return low + 1
    }

    fun columnOf(offset: Int): Int = offset - starts[lineOf(offset) - 1] + 1
}

/**
 * Every current diagnostic under [wrasseDirs], rendered one per line; the entry point a build tool
 * calls in-process. Lines starting with `e: ` are error-level findings. [projectDir], when given,
 * resolves a report's stored paths (see [WReportReplay]); [applyResult], when given, is the
 * outcome of an apply run over these same directories, used to reconcile the findings of every
 * file that apply just rewrote (see [WReportReplay.collect]).
 */
@JvmOverloads
fun replayReports(
    wrasseDirs: List<String>,
    projectDir: Path? = null,
    applyResult: ApplyResult? = null,
): List<String> {
    val lines = ArrayList<String>()
    for (dir in wrasseDirs) {
        for (file in WReportReplay.collect(Path.of(dir), projectDir, applyResult)) {
            for (diagnostic in file.diagnostics) lines.add(WReportReplay.render(file, diagnostic, projectDir))
        }
    }
    return lines
}
