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
     * `[startOffset, endOffset)` shifts by `replacementLength - (endOffset - startOffset)`. One
     * that falls inside a span whose [AppliedEdit.originalText] is a single line has no matching
     * position and is dropped, same as before. Inside a span spanning more than one line (the
     * `format` rule's edit replaces the whole file), [LineDiffMapper] instead diffs
     * [AppliedEdit.originalText] against [AppliedEdit.replacementText] line by line: an offset on
     * a line that diffed as unchanged maps to the same column on its matching line in
     * [newContent]; one on a changed line maps to column 1 of the replacement hunk that line's
     * change landed in, so it stays visible at an approximate position instead of disappearing —
     * unless every line in that hunk is empty or itself just another original line relocated
     * elsewhere by the same edit (nothing new actually took this line's place, as when reordering
     * imports drops one of them), in which case there is no position left to point at and the
     * entry is dropped, same as a single-line span. Line and column of a surviving offset are
     * recomputed against [newContent] with the same 1-based convention the compiler plugin
     * records them with.
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
            if (offset < edit.endOffset) return mapInsideEdit(offset, edit, shift)
            shift += edit.replacementLength - (edit.endOffset - edit.startOffset)
        }
        return offset + shift
    }

    private fun mapInsideEdit(
        offset: Int,
        edit: AppliedEdit,
        shiftBeforeEdit: Int,
    ): Int? {
        if (!edit.originalText.contains('\n')) return null
        val relOffset = offset - edit.startOffset
        val mappedRel = LineDiffMapper.mapOffset(edit.originalText, edit.replacementText, relOffset) ?: return null
        return edit.startOffset + shiftBeforeEdit + mappedRel
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

    private fun resolvePath(
        filePath: String,
        projectDir: Path?,
    ): Path = (if (projectDir != null) projectDir.resolve(filePath) else Path.of(filePath)).normalize()

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
 * Line-based diff between an edit's original and replacement text, used to map an offset inside
 * the original through to its counterpart in the replacement (see [WReportReplay.remap]). Common
 * leading/trailing lines are trimmed first, then the remainder is matched by longest-common-
 * subsequence, capped by [MAX_LCS_CELLS] so a large, pervasively rewritten file degrades to
 * hunk-start mapping instead of doing unbounded work.
 */
private object LineDiffMapper {
    private const val MAX_LCS_CELLS = 4_000_000L

    fun mapOffset(
        original: String,
        replacement: String,
        relOffset: Int,
    ): Int? {
        val originalLines = original.split('\n')
        val replacementLines = replacement.split('\n')
        val originalStarts = lineStarts(originalLines)
        val replacementStarts = lineStarts(replacementLines)
        val lineIndex = lineIndexFor(originalStarts, relOffset)
        val matchedLine = matchLines(originalLines, replacementLines)

        val matchedReplacementIndex = matchedLine[lineIndex]
        if (matchedReplacementIndex >= 0) {
            val column = relOffset - originalStarts[lineIndex]
            return replacementStarts[matchedReplacementIndex] + column
        }
        var lastMatched = -1
        for (i in lineIndex downTo 0) {
            if (matchedLine[i] >= 0) {
                lastMatched = matchedLine[i]
                break
            }
        }
        var nextMatched = replacementLines.size
        for (i in lineIndex until originalLines.size) {
            if (matchedLine[i] >= 0) {
                nextMatched = matchedLine[i]
                break
            }
        }
        val hunkStart = lastMatched + 1
        if (hunkStart >= nextMatched) return null
        val hunkIsWhollyRelocatedLines = (hunkStart until nextMatched).all { bi ->
            val line = replacementLines[bi]
            line.isEmpty() || originalLines.any { it == line }
        }
        if (hunkIsWhollyRelocatedLines) return null
        return if (hunkStart < replacementStarts.size) replacementStarts[hunkStart] else replacement.length
    }

    private fun lineStarts(lines: List<String>): IntArray {
        val starts = IntArray(lines.size)
        var pos = 0
        for (i in lines.indices) {
            starts[i] = pos
            pos += lines[i].length + 1
        }
        return starts
    }

    private fun lineIndexFor(starts: IntArray, offset: Int): Int {
        var low = 0
        var high = starts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (starts[mid] <= offset) low = mid else high = mid - 1
        }
        return low
    }

    private fun matchLines(a: List<String>, b: List<String>): IntArray {
        val result = IntArray(a.size) { -1 }
        var prefix = 0
        val maxPrefix = minOf(a.size, b.size)
        while (prefix < maxPrefix && a[prefix] == b[prefix]) prefix++
        for (i in 0 until prefix) result[i] = i

        var suffix = 0
        val maxSuffix = maxPrefix - prefix
        while (suffix < maxSuffix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++
        for (i in 0 until suffix) result[a.size - 1 - i] = b.size - 1 - i

        val midA = a.subList(prefix, a.size - suffix)
        val midB = b.subList(prefix, b.size - suffix)
        if (midA.isNotEmpty() && midB.isNotEmpty() && midA.size.toLong() * midB.size.toLong() <= MAX_LCS_CELLS) {
            for ((ai, bi) in longestCommonSubsequence(midA, midB)) result[prefix + ai] = prefix + bi
        }
        return result
    }

    private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val pairs = ArrayList<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    pairs.add(i to j)
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return pairs
    }
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
