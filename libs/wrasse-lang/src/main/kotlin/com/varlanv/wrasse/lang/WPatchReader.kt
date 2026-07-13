package com.varlanv.wrasse.lang

/**
 * Parses wrasse text patch files produced by [WPatchWriter] back into [FileEdits].
 * Ignores comment lines (starting with `#`). Unescapes `\n` → newline, `\\` → backslash.
 */
object WPatchReader {

    fun read(input: CharSequence): List<FileEdits> {
        val result = mutableListOf<FileEdits>()
        var currentPath: String? = null
        var currentHash: String? = null
        var currentEdits = mutableListOf<WEdit>()

        for (rawLine in input.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            when {
                line.startsWith("file:") -> {
                    flushCurrent(result, currentPath, currentHash, currentEdits)
                    currentPath = line.substring(5)
                    currentHash = null
                    currentEdits = mutableListOf()
                }
                line.startsWith("hash:") -> {
                    currentHash = line.substring(5)
                }
                line.startsWith("edit:") -> {
                    val edit = parseEdit(line)
                    currentEdits.add(edit)
                }
            }
        }
        flushCurrent(result, currentPath, currentHash, currentEdits)
        return result
    }

    private fun flushCurrent(
        result: MutableList<FileEdits>,
        path: String?,
        hash: String?,
        edits: MutableList<WEdit>,
    ) {
        if (path != null && hash != null && edits.isNotEmpty()) {
            result.add(FileEdits(path, hash, edits.toList()))
        }
    }

    private fun parseEdit(line: String): WEdit {
        val afterPrefix = line.substring(5)
        val firstColon = afterPrefix.indexOf(':')
        if (firstColon < 0) throw IllegalArgumentException("Malformed edit line: $line")
        val secondColon = afterPrefix.indexOf(':', firstColon + 1)
        if (secondColon < 0) throw IllegalArgumentException("Malformed edit line: $line")
        val startOffset = afterPrefix.substring(0, firstColon).toInt()
        val endOffset = afterPrefix.substring(firstColon + 1, secondColon).toInt()
        val escapedReplacement = afterPrefix.substring(secondColon + 1)
        return WEdit(startOffset, endOffset, unescapeReplacement(escapedReplacement))
    }

    private fun unescapeReplacement(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(ch); i++ }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
