package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit

/**
 * One standalone import-insertion edit and the ASCII-sorted target FQNs it carries — several new
 * imports landing at the identical seam are merged into a single edit, sorted among themselves,
 * so [ImportEngine] never has to reason about same-offset zero-width tie-breaking for its own
 * insertions.
 */
class ImportInsertionGroup(val edit: WEdit, val fqns: List<String>)

/**
 * Pure placement logic for a brand-new `import <fqn>` directive that can't ride
 * `import-ordering`'s own composed whole-list rewrite
 * ([ImportOrderingDecision.composeRegion]'s `extraLines` owns that path instead): ordering
 * disabled or the list isn't clean ([standaloneEdits]), and no import directives exist at all
 * ([emptyListInsertion]). Compiler-free, unit-testable without kotlinc.
 *
 * Every [standaloneEdits] insertion is a zero-width [WEdit] (pure text growth, never overwriting
 * existing directive text) landing at one of two seams: immediately before the first existing
 * directive whose [ImportOrderingRecord.sortKey] sorts after the new import, but only when the
 * gap to its predecessor (or [listStart] for the first directive) is a single, comment-free `\n`
 * ([ImportOrderingDecision.isCleanList]'s "clean pairwise gap" check, applied to just that one
 * seam); or after the last existing directive ([listEnd], always its `endOffset`), used when the
 * new import sorts last and as the fallback whenever the seam above isn't clean — falling back
 * there can never sever a comment from the directive it documents, at the cost of a
 * not-strictly-alphabetical position for that one import.
 */
object ImportInsertionDecision {
    fun standaloneEdits(
        sourceText: CharSequence,
        listStart: Int,
        listEnd: Int,
        directiveRecords: List<ImportOrderingRecord>,
        newImportFqns: List<String>,
    ): List<ImportInsertionGroup> {
        if (newImportFqns.isEmpty() || directiveRecords.isEmpty()) return emptyList()

        val bySeam = LinkedHashMap<Int, MutableList<String>>()
        for (fqn in newImportFqns) {
            val insertionIndex = directiveRecords
                .indexOfFirst { it.sortKey > fqn }
                .let { if (it < 0) directiveRecords.size else it }
            val seam = seamOffsetFor(sourceText, listStart, listEnd, directiveRecords, insertionIndex)
            bySeam.getOrPut(seam) { mutableListOf() }.add(fqn)
        }
        return bySeam.map { (offset, fqns) ->
            val sorted = fqns.sorted()
            val text =
                if (offset == listEnd) {
                    "\n" + sorted.joinToString("\n") { "import $it" }
                } else {
                    sorted.joinToString("") { "import $it\n" }
                }
            ImportInsertionGroup(WEdit(offset, offset, text), sorted)
        }
    }

    /**
     * Replaces the whitespace-only gap between [listStart] (the zero-width `IMPORT_LIST` position,
     * present even in a file with zero import directives) and the first non-whitespace content
     * after it with a canonical rendering: a blank line before the new import block when
     * something (a package directive and/or file annotations) precedes it, a blank line after
     * when something follows, neither when there is nothing on that side.
     */
    fun emptyListInsertion(
        sourceText: CharSequence,
        listStart: Int,
        newImportFqns: List<String>,
    ): WEdit {
        var contentStart = listStart
        while (contentStart < sourceText.length && sourceText[contentStart].isWhitespace()) contentStart++
        val hasPrecedingContent = listStart > 0
        val hasFollowingContent = contentStart < sourceText.length
        val block = newImportFqns.sorted().joinToString("\n") { "import $it" }
        val prefix = if (hasPrecedingContent) "\n\n" else ""
        val suffix = if (hasFollowingContent) "\n\n" else "\n"
        return WEdit(listStart, contentStart, "$prefix$block$suffix")
    }

    private fun seamOffsetFor(
        sourceText: CharSequence,
        listStart: Int,
        listEnd: Int,
        directiveRecords: List<ImportOrderingRecord>,
        insertionIndex: Int,
    ): Int {
        if (insertionIndex >= directiveRecords.size) return listEnd
        val nextStart = directiveRecords[insertionIndex].startOffset
        if (insertionIndex == 0) {
            return if (nextStart == listStart) listStart else listEnd
        }
        val prevEnd = directiveRecords[insertionIndex - 1].endOffset
        val gapIsClean = nextStart - prevEnd == 1 && sourceText[prevEnd] == '\n'
        return if (gapIsClean) nextStart else listEnd
    }
}
