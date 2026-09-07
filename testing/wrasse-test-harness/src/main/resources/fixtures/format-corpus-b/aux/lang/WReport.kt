package com.varlanv.wrasse.lang

/**
 * One diagnostic wrasse reported for a file: 1-based [line]/[column], its [offset] into the
 * source, the rule's configured [level] (`error` or `warn`), whether the report carried at least
 * one autofix edit ([fixable]), and the [message] as printed after the `wrasse: ` prefix.
 */
class ReportedDiagnostic(
    val line: Int,
    val column: Int,
    val offset: Int,
    val level: String,
    val fixable: Boolean,
    val message: String,
) {
    val isError: Boolean get() = level == LEVEL_ERROR

    companion object {
        const val LEVEL_ERROR = "error"
        const val LEVEL_WARN = "warn"
    }
}

/** Every diagnostic one compile reported for [filePath], with the SHA-256 of the source it saw. */
class ReportedFile(
    val filePath: String,
    val sourceHash: String,
    val diagnostics: List<ReportedDiagnostic>,
) {
    fun sameContent(other: ReportedFile): Boolean {
        if (sourceHash != other.sourceHash || diagnostics.size != other.diagnostics.size) return false
        for (i in diagnostics.indices) {
            val a = diagnostics[i]
            val b = other.diagnostics[i]
            if (a.line != b.line ||
                a.column != b.column ||
                a.offset != b.offset ||
                a.level != b.level ||
                a.fixable != b.fixable ||
                a.message != b.message
            ) {
                return false
            }
        }
        return true
    }
}

const val REPORT_HEADER = "# wrasse-report v2"

/**
 * Serializes report blocks: `file:`/`hash:` then one
 * `diag:<line>:<column>:<offset>:<level>:<fixable 0|1>:<message>` per diagnostic, the message with
 * `\` and newlines escaped. A tombstone is a block with the hash `-`.
 */
object WReportWriter {
    const val TOMBSTONE_HASH = "-"

    fun writeHeader(out: Appendable) {
        out.append(REPORT_HEADER).append('\n')
    }

    fun writeAll(out: Appendable, files: Collection<ReportedFile>) {
        writeHeader(out)
        for (file in files) write(out, file)
    }

    fun write(out: Appendable, file: ReportedFile) {
        out.append("file:").append(file.filePath).append('\n')
        out.append("hash:").append(file.sourceHash).append('\n')
        for (diagnostic in file.diagnostics) {
            out
                .append("diag:")
                .append(diagnostic.line.toString())
                .append(':')
                .append(diagnostic.column.toString())
                .append(':')
                .append(diagnostic.offset.toString())
                .append(':')
                .append(diagnostic.level)
                .append(':')
                .append(if (diagnostic.fixable) "1" else "0")
                .append(':')
                .append(escape(diagnostic.message))
                .append('\n')
        }
    }

    fun writeTombstone(out: Appendable, filePath: String) {
        out.append("file:").append(filePath).append('\n')
        out.append("hash:").append(TOMBSTONE_HASH).append('\n')
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\n", "\\n")
}

/**
 * Reads a report journal: later blocks for a path replace earlier ones and a tombstone forgets the
 * path. A file whose first line is not exactly [REPORT_HEADER] — no header at all, or an older or
 * unrecognized one — is treated as an empty report rather than parsed or rejected, so a report
 * written by a different wrasse version is simply forgotten instead of misread.
 */
object WReportReader {
    class Journal(val entries: List<ReportedFile>, val blockCount: Int)

    fun read(input: CharSequence): List<ReportedFile> = readJournal(input).entries

    fun readJournal(input: CharSequence): Journal {
        val lines = input.lineSequence().iterator()
        if (!lines.hasNext() || lines.next() != REPORT_HEADER) return Journal(emptyList(), 0)

        val byPath = LinkedHashMap<String, ReportedFile>()
        var blockCount = 0
        var currentPath: String? = null
        var currentHash: String? = null
        var current = ArrayList<ReportedDiagnostic>()

        fun flush() {
            val path = currentPath ?: return
            blockCount++
            if (currentHash == WReportWriter.TOMBSTONE_HASH) {
                byPath.remove(path)
            } else {
                byPath.remove(path)
                byPath[path] = ReportedFile(path, currentHash ?: "", current)
            }
            currentPath = null
            currentHash = null
            current = ArrayList()
        }

        while (lines.hasNext()) {
            val line = lines.next()
            when {
                line.startsWith("file:") -> {
                    flush()
                    currentPath = line.substring(5)
                }
                line.startsWith("hash:") -> currentHash = line.substring(5)
                line.startsWith("diag:") -> parseDiagnostic(line.substring(5))?.let { current.add(it) }
                else -> {}
            }
        }
        flush()
        return Journal(byPath.values.toList(), blockCount)
    }

    private fun parseDiagnostic(body: String): ReportedDiagnostic? {
        val c1 = body.indexOf(':')
        if (c1 < 0) return null
        val c2 = body.indexOf(':', c1 + 1)
        if (c2 < 0) return null
        val c3 = body.indexOf(':', c2 + 1)
        if (c3 < 0) return null
        val c4 = body.indexOf(':', c3 + 1)
        if (c4 < 0) return null
        val c5 = body.indexOf(':', c4 + 1)
        if (c5 < 0) return null
        val line = body.substring(0, c1).toIntOrNull() ?: return null
        val column = body.substring(c1 + 1, c2).toIntOrNull() ?: return null
        val offset = body.substring(c2 + 1, c3).toIntOrNull() ?: return null
        val level = body.substring(c3 + 1, c4)
        val fixable = body.substring(c4 + 1, c5) == "1"
        return ReportedDiagnostic(line, column, offset, level, fixable, unescape(body.substring(c5 + 1)))
    }

    private fun unescape(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val next = s[i + 1]
                if (next == 'n') {
                    out.append('\n')
                    i += 2
                    continue
                }
                if (next == '\\') {
                    out.append('\\')
                    i += 2
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
