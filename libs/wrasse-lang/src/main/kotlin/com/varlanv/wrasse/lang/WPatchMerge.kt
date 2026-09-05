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
object WPatchMerge {
    fun upsert(existing: List<FileEdits>, entry: FileEdits): List<FileEdits> {
        var replaced = false
        val result = existing.map {
            if (it.filePath == entry.filePath) {
                replaced = true
                entry
            } else {
                it
            }
        }
        return if (replaced) result else result + entry
    }

    fun remove(
        existing: List<FileEdits>,
        filePath: String,
    ): List<FileEdits> = existing.filterNot { it.filePath == filePath }
}
