package com.varlanv.wrasse.lang

/** One diagnostic wrasse reported for a file: 1-based [line]/[column], the rule's configured level (`error` or `warn`), and the message as printed after the `wrasse: ` prefix. */
class ReportedDiagnostic(
    val line: Int,
    val column: Int,
    val level: String,
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
            if (a.line != b.line || a.column != b.column || a.level != b.level || a.message != b.message) return false
        }
        return true
    }
}

/**
 * Serializes report blocks: `file:`/`hash:` then one `diag:<line>:<column>:<level>:<message>` per
 * diagnostic, the message with `\` and newlines escaped. A tombstone is a block with the hash `-`.
 */
object WReportWriter {
    const val TOMBSTONE_HASH = "-"

    fun writeHeader(out: Appendable) {
        out.append("# wrasse-report v1\n")
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
                .append(diagnostic.level)
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

/** Reads a report journal: later blocks for a path replace earlier ones and a tombstone forgets the path. */
object WReportReader {
    class Journal(val entries: List<ReportedFile>, val blockCount: Int)

    fun read(input: CharSequence): List<ReportedFile> = readJournal(input).entries

    fun readJournal(input: CharSequence): Journal {
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

        for (line in input.lineSequence()) {
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
        val first = body.indexOf(':')
        if (first < 0) return null
        val second = body.indexOf(':', first + 1)
        if (second < 0) return null
        val third = body.indexOf(':', second + 1)
        if (third < 0) return null
        val line = body.substring(0, first).toIntOrNull() ?: return null
        val column = body.substring(first + 1, second).toIntOrNull() ?: return null
        val level = body.substring(second + 1, third)
        return ReportedDiagnostic(line, column, level, unescape(body.substring(third + 1)))
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
