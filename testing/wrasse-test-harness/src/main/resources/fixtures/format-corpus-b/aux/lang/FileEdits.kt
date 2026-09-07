package com.varlanv.wrasse.lang

class FileEdits(
    val filePath: String,
    val sourceHash: String,
    val edits: List<WEdit>,
) {
    /** True if [other] records the same hash and the same edit spans and replacements, in order. */
    fun sameContent(other: FileEdits): Boolean {
        if (sourceHash != other.sourceHash || edits.size != other.edits.size) return false
        for (i in edits.indices) {
            val x = edits[i]
            val y = other.edits[i]
            if (x.startOffset != y.startOffset || x.endOffset != y.endOffset || x.replacement != y.replacement) {
                return false
            }
        }
        return true
    }
}
