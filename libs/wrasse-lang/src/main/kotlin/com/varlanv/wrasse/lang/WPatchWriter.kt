package com.varlanv.wrasse.lang

/**
 * Writes [FileEdits] to the wrasse text patch format.
 *
 * Format:
 * ```
 * # wrasse-fixes v1
 * file:<path>
 * hash:<sha256hex>
 * edit:<startOffset>:<endOffset>:<escaped-replacement>
 * ```
 *
 * Edits within a file block are sorted in descending offset order.
 * Replacement escaping: newline → `\n`, backslash → `\\`.
 */
object WPatchWriter {
    fun writeHeader(out: Appendable) {
        out.append("# wrasse-fixes v1\n")
    }

    fun writeAll(out: Appendable, allFileEdits: Collection<FileEdits>) {
        writeHeader(out)
        for (fileEdits in allFileEdits) {
            write(out, fileEdits)
        }
    }

    fun write(out: Appendable, fileEdits: FileEdits) {
        out.append("file:").append(fileEdits.filePath).append('\n')
        out.append("hash:").append(fileEdits.sourceHash).append('\n')
        val sorted = fileEdits.edits.sortedByDescending { it.startOffset }
        for (edit in sorted) {
            out
                .append("edit:")
                .append(edit.startOffset.toString())
                .append(':')
                .append(edit.endOffset.toString())
                .append(':')
                .append(escapeReplacement(edit.replacement))
                .append('\n')
        }
    }

    private fun escapeReplacement(s: String): String =
    s.replace("\\", "\\\\").replace("\n", "\\n")
}
