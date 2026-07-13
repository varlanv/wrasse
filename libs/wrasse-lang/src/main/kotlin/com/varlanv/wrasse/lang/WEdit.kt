package com.varlanv.wrasse.lang

/**
 * A single offset-based source edit. Edits are collected during the SAX walk and
 * written to a patch file for later application.
 *
 * - [startOffset] == [endOffset] with non-empty [replacement] → insertion.
 * - Non-empty range with empty [replacement] → deletion.
 * - Non-empty range with non-empty [replacement] → replacement.
 */
class WEdit(
    val startOffset: Int,
    val endOffset: Int,
    val replacement: String,
)
