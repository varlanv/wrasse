package com.varlanv.wrasse.lang

/**
 * Serializes patch blocks. A file block is `file:`/`hash:` followed by its edits, start offsets
 * descending; [writeTombstone] is a block with the hash `-` and no edits, which [WPatchReader]
 * takes as "forget every earlier block for this path".
 */
object WPatchWriter {
    const val TOMBSTONE_HASH = "-"

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

    fun writeTombstone(out: Appendable, filePath: String) {
        out.append("file:").append(filePath).append('\n')
        out.append("hash:").append(TOMBSTONE_HASH).append('\n')
    }

    private fun escapeReplacement(s: String): String = s.replace("\\", "\\\\").replace("\n", "\\n")
}
