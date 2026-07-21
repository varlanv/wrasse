package com.varlanv.wrasse.lang

/**
 * A single offset-based source edit. Edits are collected during the SAX walk and
 * written to a patch file for later application.
 *
 * - [startOffset] == [endOffset] with non-empty [replacement] → insertion.
 * - Non-empty range with empty [replacement] → deletion.
 * - Non-empty range with non-empty [replacement] → replacement.
 *
 * [indentScope] is consumed only by the printer's splicer
 * (`com.varlanv.wrasse.format.DocSplicer`), never by [com.varlanv.wrasse.lang.WPatchWriter] or
 * [com.varlanv.wrasse.lang.WPatchApplier]: [IndentScope.OPEN] marks an edit whose insertion point
 * begins a region that should render one ambient indent depth deeper than its surroundings;
 * [IndentScope.CLOSE] marks the matching edit that ends it. A rule that computes its own
 * indentation (the [IndentScope.NONE] default, every existing edit) never sets this.
 */
class WEdit(val startOffset: Int, val endOffset: Int, val replacement: String, val indentScope: IndentScope = IndentScope.NONE)

enum class IndentScope {
    NONE,
    OPEN,
    CLOSE,
}
