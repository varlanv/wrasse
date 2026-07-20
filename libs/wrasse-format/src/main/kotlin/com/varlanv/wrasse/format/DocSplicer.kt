package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit

/**
 * Splices [EditPlan][com.varlanv.wrasse.model.EditPlan] edits into a `Doc` tree (§5.3's
 * "content → layout" contract): every remaining rule fix, cast as an atomic `Doc.Text` run at its
 * original span, replaces whatever `Doc` content that span used to cover. Layout then renders the
 * result without ever re-deriving tokens, so a spliced edit can never re-trigger a rule.
 *
 * [splice] applies each [WEdit] independently against [Doc.start]/[Doc.end] spans, never against
 * rendered text — edits are pairwise disjoint by construction ([EditPlan][com.varlanv.wrasse.model.EditPlan]'s
 * own invariant), so application order never matters. A single edit may:
 * - fully cover one or more `Doc` leaves/subtrees (whole-leaf or whole-subtree replacement) —
 *   the first covered node becomes the replacement `Text`, every further covered node this same
 *   edit reaches is dropped (it would otherwise duplicate the same replacement text);
 * - land inside a single `Doc.Text` leaf (always cleanly splittable — a `Text.value` is always
 *   the exact source substring of its span) or a `Doc.Break` leaf (splittable only up to its
 *   [Doc.Break.literal]'s represented length; a cut strictly inside the elided trailing-indent
 *   tail cannot be honored and is refused, see below);
 * - be a zero-width insertion, threaded to the exactly one leaf whose span starts at the
 *   insertion point (or, at true end-of-file, appended after everything).
 *
 * Returns `null` if any edit cannot be cleanly mapped this way — never guessed at (§5.3): a
 * `Doc.Break`'s elided trailing-indent tail (the whitespace `Layout` regenerates instead of
 * storing) has no addressable position for a cut to land on other than its own two ends.
 */
object DocSplicer {

    fun splice(doc: Doc, edits: List<WEdit>): Doc? {
        var current = doc
        for (edit in edits) {
            current = spliceOne(current, edit) ?: return null
        }
        return current
    }

    private fun spliceOne(doc: Doc, edit: WEdit): Doc? {
        if (edit.startOffset == edit.endOffset && edit.startOffset >= doc.end) {
            return Doc.Concat(listOf(doc, Doc.Text(edit.replacement, edit.startOffset, edit.endOffset)), doc.start, edit.endOffset)
        }

        var emitted = false

        fun emitReplacementOnce(): Doc? =
            if (!emitted) {
                emitted = true
                Doc.Text(edit.replacement, edit.startOffset, edit.endOffset)
            } else {
                null
            }

        fun splitText(node: Doc.Text): Doc {
            val len = node.value.length
            val localStart = (edit.startOffset - node.start).coerceIn(0, len)
            val localEnd = (edit.endOffset - node.start).coerceIn(0, len)
            val parts = mutableListOf<Doc>()
            if (localStart > 0) parts.add(Doc.Text(node.value.substring(0, localStart), node.start, node.start + localStart))
            emitReplacementOnce()?.let { parts.add(it) }
            if (localEnd < len) parts.add(Doc.Text(node.value.substring(localEnd), node.start + localEnd, node.end))
            return Doc.Concat(parts, node.start, node.end)
        }

        fun splitBreak(node: Doc.Break): Doc? {
            val representedEnd = node.start + node.literal.length
            var prefix: Doc.Break? = null
            var suffix: Doc.Break? = null
            if (edit.startOffset > node.start) {
                val cut = edit.startOffset
                if (cut in (representedEnd + 1) until node.end) return null
                val local = cut - node.start
                prefix = Doc.Break(node.kind, node.literal.substring(0, local), node.flat, node.start, cut)
            }
            if (edit.endOffset < node.end) {
                val cut = edit.endOffset
                if (cut in (representedEnd + 1) until node.end) return null
                val local = cut - node.start
                if (local < node.literal.length) {
                    suffix = Doc.Break(node.kind, node.literal.substring(local), node.flat, cut, node.end)
                }
            }
            val middle = emitReplacementOnce()
            return Doc.Concat(listOfNotNull(prefix, middle, suffix), node.start, node.end)
        }

        fun rec(node: Doc): Doc? {
            val isInsertion = edit.startOffset == edit.endOffset
            val touches = if (isInsertion) {
                node.start <= edit.startOffset && edit.startOffset < node.end
            } else {
                edit.startOffset < node.end && node.start < edit.endOffset
            }
            if (!touches) return node

            val fullyCovered = !isInsertion && edit.startOffset <= node.start && node.end <= edit.endOffset
            if (fullyCovered) {
                return emitReplacementOnce() ?: Doc.Concat(emptyList(), node.start, node.end)
            }

            return when (node) {
                is Doc.Text -> splitText(node)
                is Doc.Break -> splitBreak(node)
                is Doc.Indent -> rec(node.body)?.let { Doc.Indent(it) }
                is Doc.Group -> rec(node.body)?.let { Doc.Group(it) }
                is Doc.Concat -> {
                    val newParts = ArrayList<Doc>(node.parts.size)
                    for (part in node.parts) {
                        newParts.add(rec(part) ?: return null)
                    }
                    Doc.Concat(newParts, node.start, node.end)
                }
            }
        }

        return rec(doc)
    }
}
