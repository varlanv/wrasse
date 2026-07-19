package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * One import directive's own span and verbatim source text, in document order, as recorded by
 * [ImportOrderingRule] off the leaf stream. [sortKey] is the directive's own text with the
 * leading `import` keyword and its following whitespace stripped — `import a.b.C` sorts by
 * `a.b.C`, `import a.b.C as D` sorts by `a.b.C as D` (so aliased duplicates of the same FQN order
 * deterministically by their alias), `import a.b.*` sorts by `a.b.*`.
 */
class ImportOrderingRecord(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
) {
    val sortKey: String = ImportOrderingDecision.sortKeyOf(text)
}

/**
 * Pure verdict and composition logic for `import-ordering`, compiler-free so it is unit-testable
 * without a kotlinc dependency. Plain ASCII-alphabetical order on [ImportOrderingRecord.sortKey],
 * no grouping, no config knob.
 */
object ImportOrderingDecision {

    fun sortKeyOf(directiveText: String): String = directiveText.removePrefix("import").trimStart()

    /** The first record (in document order) whose position disagrees with ascending [ImportOrderingRecord.sortKey] order, or `null` if already sorted. */
    fun firstOutOfOrder(records: List<ImportOrderingRecord>): ImportOrderingRecord? {
        if (records.size < 2) return null
        val sortedKeys = records.map { it.sortKey }.sorted()
        for (i in records.indices) {
            if (records[i].sortKey != sortedKeys[i]) return records[i]
        }
        return null
    }

    fun sortedReplacement(records: List<ImportOrderingRecord>): String =
        records.sortedBy { it.sortKey }.joinToString("\n") { it.text }

    /**
     * True iff `[listStart, listEnd)` is exactly a sequence of import directives (`directiveSpans`,
     * in document order), each separated from the next by a single `\n` and nothing else, with no
     * leading/trailing slack and no comment leaf recorded anywhere inside the list
     * ([hasCommentInList]). Any deviation — a comment, a blank line, two directives sharing a
     * line — means reordering could sever a comment from the directive it documents or otherwise
     * change something other than order, so the caller must not attempt a fix.
     */
    fun isCleanList(
        sourceText: CharSequence,
        listStart: Int,
        listEnd: Int,
        directiveSpans: List<Pair<Int, Int>>,
        hasCommentInList: Boolean,
    ): Boolean {
        if (hasCommentInList) return false
        if (directiveSpans.isEmpty()) return false
        if (directiveSpans.first().first != listStart) return false
        if (directiveSpans.last().second != listEnd) return false
        for (i in 0 until directiveSpans.size - 1) {
            val gapStart = directiveSpans[i].second
            val gapEnd = directiveSpans[i + 1].first
            if (gapEnd - gapStart != 1) return false
            if (sourceText[gapStart] != '\n') return false
        }
        return true
    }

    /**
     * Applies [edits] (attributed to other, already-run import rules) to a local copy of
     * `sourceText[regionStart, regionEnd)`, then re-splits and re-sorts the result. Returns the
     * replacement text for the whole region, or `null` if [edits] overlap each other or the
     * result does not parse as "zero or more `import ...` lines separated by `\n`" — the caller
     * must not guess in that case and must leave [edits] in place instead of using this result.
     */
    fun composeRegion(sourceText: CharSequence, regionStart: Int, regionEnd: Int, edits: List<WEdit>): String? {
        val descending = edits.sortedWith(compareByDescending<WEdit> { it.startOffset }.thenByDescending { it.endOffset })
        for (i in 0 until descending.size - 1) {
            if (descending[i + 1].endOffset > descending[i].startOffset) return null
        }
        val buffer = StringBuilder(sourceText.subSequence(regionStart, regionEnd).toString())
        for (edit in descending) {
            buffer.replace(edit.startOffset - regionStart, edit.endOffset - regionStart, edit.replacement)
        }
        val reconstructed = buffer.toString()
        val endsWithNewline = reconstructed.endsWith("\n")
        val body = if (endsWithNewline) reconstructed.removeSuffix("\n") else reconstructed
        val lines = if (body.isEmpty()) emptyList() else body.split("\n")
        if (lines.any { !it.startsWith("import") }) return null
        val sortedBody = lines.sortedBy { sortKeyOf(it) }.joinToString("\n")
        return if (endsWithNewline) "$sortedBody\n" else sortedBody
    }
}
