package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType

/**
 * One `IMPORT_DIRECTIVE` reconstructed from the leaf stream: its dotted path segments, optional
 * alias, whether it ends in `.*`, and its own node span.
 */
class RawImportDirective(
    val pathParts: List<String>,
    val aliasName: String?,
    val isStar: Boolean,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Stateful per-file assembler that reconstructs one import directive's dotted path, optional
 * alias, and star-import flag from the leaf stream, used by [ImportEngine]'s single walk-side
 * assembly instead of re-deriving the same `IMPORT_DIRECTIVE`/`IMPORT_ALIAS`/`MUL` leaf
 * bookkeeping per id.
 *
 * One instance per rule instance (already fresh per file, per
 * [com.varlanv.wrasse.model.WUninitializedRuleGroup.initGroup]). Call [enterImportDirective] on
 * `IMPORT_DIRECTIVE` enter, [visitLeaf] for every leaf while [WContext.hasAncestor]
 * `IMPORT_DIRECTIVE` is true, and [exitImportDirective] on its exit.
 */
class ImportDirectiveAssembler {
    private var pathParts = mutableListOf<String>()
    private var aliasName: String? = null
    private var starSeen = false
    private var directiveStart = -1

    fun enterImportDirective(startOffset: Int) {
        pathParts = mutableListOf()
        aliasName = null
        starSeen = false
        directiveStart = startOffset
    }

    fun visitLeaf(ctx: WContext) {
        when (ctx.type) {
            WNodeType.MUL -> starSeen = true
            WNodeType.IDENTIFIER -> {
                val text = ctx.leafText?.toString()?.removeSurrounding("`") ?: return
                if (ctx.hasAncestor(WNodeType.IMPORT_ALIAS)) {
                    aliasName = text
                } else {
                    pathParts.add(text)
                }
            }

            else -> {}
        }
    }

    fun exitImportDirective(endOffset: Int): RawImportDirective =
        RawImportDirective(pathParts, aliasName, starSeen, directiveStart, endOffset)
}
