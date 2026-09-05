package com.varlanv.wrasse.lang

/**
 * Pure merge-on-write logic for one compilation's in-memory patch state.
 *
 * A compilation loads its existing [FileEdits] once, then for every file it recompiles either
 * [upsert]s a fresh entry (replacing any stale one for that path) or [remove]s the entry entirely
 * when the recompiled file now produces zero edits (self-cleaning). Order of the untouched entries
 * is preserved; an upserted entry keeps its original position if the path already existed, or is
 * appended if it is new.
 */
/**
 * Merge steps over a patch file's entries. Both operations return [existing] itself, same
 * instance, when they would change nothing — a caller compares identity to skip a rewrite.
 */
object WPatchMerge {
    fun upsert(existing: List<FileEdits>, entry: FileEdits): List<FileEdits> {
        val index = existing.indexOfFirst { it.filePath == entry.filePath }
        if (index < 0) return existing + entry
        if (sameContent(existing[index], entry)) return existing
        val result = ArrayList(existing)
        result[index] = entry
        return result
    }

    fun remove(
        existing: List<FileEdits>,
        filePath: String,
    ): List<FileEdits> = if (existing.none { it.filePath == filePath }) existing else existing.filterNot { it.filePath == filePath }

    private fun sameContent(a: FileEdits, b: FileEdits): Boolean {
        if (a.sourceHash != b.sourceHash || a.edits.size != b.edits.size) return false
        for (i in a.edits.indices) {
            val x = a.edits[i]
            val y = b.edits[i]
            if (x.startOffset != y.startOffset || x.endOffset != y.endOffset || x.replacement != y.replacement) return false
        }
        return true
    }
}
