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
 * Pure placement logic for a brand-new `import <fqn>` directive `no-unnecessary-fqn` (D.3,
 * design.md §8) needs to add, for the two cases that can't ride `import-ordering`'s own composed
 * whole-list rewrite ([ImportOrderingDecision.composeRegion]'s `extraLines` parameter owns that
 * path instead): ordering disabled or the list isn't clean ([standaloneEdits]), and no import
 * directives exist at all ([emptyListInsertion]). Compiler-free, unit-testable without kotlinc.
 *
 * Every [standaloneEdits] insertion is a **zero-width** [WEdit] (pure text growth, never
 * overwriting a byte of existing directive text) landing at one of two kinds of seam:
 * - immediately before the first existing directive whose [ImportOrderingRecord.sortKey] sorts
 *   after the new import — only when the gap between it and its predecessor (or [listStart] for
 *   the very first directive) is a single, comment-free `\n`, the same "clean pairwise gap" fact
 *   [ImportOrderingDecision.isCleanList] establishes for the whole list, checked here for just the
 *   one seam;
 * - after the last existing directive ([listEnd] — always exactly the last directive's own
 *   `endOffset`, design.md §8's "last-directive-removed" finding), used both when the new import
 *   sorts last and as the universal fallback whenever the seam above isn't clean.
 *
 * A dirty pairwise gap (most commonly a comment documenting the *next* directive) is never
 * risked: falling back to "after the last directive" can never sever a comment from what it
 * documents, at the cost of a not-strictly-alphabetical position for that one import — consistent
 * with the rest of the import family's bail-toward-safety posture rather than guessing.
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
            val insertionIndex = directiveRecords.indexOfFirst { it.sortKey > fqn }
                .let { if (it < 0) directiveRecords.size else it }
            val seam = seamOffsetFor(sourceText, listStart, listEnd, directiveRecords, insertionIndex)
            bySeam.getOrPut(seam) { mutableListOf() }.add(fqn)
        }
        return bySeam.map { (offset, fqns) ->
            val sorted = fqns.sorted()
            val text = if (offset == listEnd) {
                "\n" + sorted.joinToString("\n") { "import $it" }
            } else {
                sorted.joinToString("") { "import $it\n" }
            }
            ImportInsertionGroup(WEdit(offset, offset, text), sorted)
        }
    }

    /**
     * Replaces the whitespace-only gap between [listStart] (the zero-width `IMPORT_LIST` position
     * — an import list node is present, empty, even in a file with zero import directives at all,
     * design.md §8) and the first non-whitespace content after it with a canonical, born-clean
     * rendering: a blank line before the new import block when something (a package directive
     * and/or file annotations) precedes it, a blank line after when something follows, neither
     * when there is nothing on that side.
     */
    fun emptyListInsertion(sourceText: CharSequence, listStart: Int, newImportFqns: List<String>): WEdit {
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
