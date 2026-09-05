package com.varlanv.wrasse.lang

/**
 * Parses a patch file as a journal: blocks are read in order, a later block for the same path
 * replaces an earlier one, and a tombstone block ([WPatchWriter.writeTombstone]) or an edit-less
 * block drops the path. An incomplete final block (a crash mid-append) is ignored; a malformed
 * edit line anywhere before it is an error.
 */
object WPatchReader {
    class Journal(val entries: List<FileEdits>, val blockCount: Int)

    fun read(input: CharSequence): List<FileEdits> = readJournal(input).entries

    fun readJournal(input: CharSequence): Journal {
        val byPath = LinkedHashMap<String, FileEdits>()
        var blockCount = 0
        var currentPath: String? = null
        var currentHash: String? = null
        var currentEdits = ArrayList<WEdit>()
        var lineStart = 0
        val length = input.length

        fun flush() {
            val path = currentPath ?: return
            blockCount++
            if (currentHash == WPatchWriter.TOMBSTONE_HASH || currentEdits.isEmpty()) {
                byPath.remove(path)
            } else {
                byPath[path] = FileEdits(path, currentHash ?: return, currentEdits)
            }
        }

        while (lineStart < length) {
            var lineEnd = input.indexOfChar('\n', lineStart)
            val terminated = lineEnd >= 0
            if (!terminated) lineEnd = length
            val line = StringSlice(input, lineStart, lineEnd)
            when {
                line.isEmpty() || line[0] == '#' -> {}
                line.startsWith("file:") -> {
                    flush()
                    currentPath = line.subSequence(5, line.length).toString()
                    currentHash = null
                    currentEdits = ArrayList()
                }

                line.startsWith("hash:") -> currentHash = line.subSequence(5, line.length).toString()
                line.startsWith("edit:") -> {
                    val edit = parseEdit(line, terminated) ?: return Journal(ArrayList(byPath.values), blockCount)
                    currentEdits.add(edit)
                }
            }
            lineStart = lineEnd + 1
        }
        if (currentPath != null && currentHash == null) return Journal(ArrayList(byPath.values), blockCount)
        flush()
        return Journal(ArrayList(byPath.values), blockCount)
    }

    private fun parseEdit(line: StringSlice, terminated: Boolean): WEdit? {
        val afterPrefix = line.subSequence(5, line.length)
        val firstColon = afterPrefix.indexOf(':')
        val secondColon = if (firstColon < 0) -1 else afterPrefix.indexOfChar(':', firstColon + 1)
        if (firstColon < 0 || secondColon < 0) {
            if (!terminated) return null
            throw IllegalArgumentException("Malformed edit line: $line")
        }
        val startOffset = afterPrefix.subSequence(0, firstColon).toString().toIntOrNull()
        val endOffset = afterPrefix.subSequence(firstColon + 1, secondColon).toString().toIntOrNull()
        if (startOffset == null || endOffset == null) {
            if (!terminated) return null
            throw IllegalArgumentException("Malformed edit line: $line")
        }
        if (!terminated) return null
        val escapedReplacement = afterPrefix.subSequence(secondColon + 1, afterPrefix.length)
        return WEdit(startOffset, endOffset, unescapeReplacement(escapedReplacement))
    }

    private fun unescapeReplacement(s: CharSequence): String {
        if (s.indexOfChar('\\') < 0) return s.toString()
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> {
                        sb.append('\n')
                        i += 2
                    }
                    '\\' -> {
                        sb.append('\\')
                        i += 2
                    }
                    else -> {
                        sb.append(ch)
                        i++
                    }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
