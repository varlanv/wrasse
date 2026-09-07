package sample

import com.varlanv.wrasse.lang.IndentScope
import com.varlanv.wrasse.lang.WEdit

/**
 * Splices [EditPlan][com.varlanv.wrasse.model.EditPlan] edits into a `Doc` tree: every remaining
 * rule fix, cast as an atomic `Doc.Text` run at its original span, replaces whatever `Doc` content
 * that span used to cover. `Layout` then renders the result without re-deriving tokens.
 *
 * [splice] first resolves every [IndentScope.OPEN]/[IndentScope.CLOSE] pair
 * ([applyIndentScopes]), then applies each [WEdit] independently against [Doc.start]/[Doc.end]
 * spans, never against rendered text — sound because edits are pairwise disjoint by construction.
 * A single edit may:
 * - fully cover one or more `Doc` leaves/subtrees: the first covered node becomes the replacement
 *   `Doc`, every further covered node this same edit reaches is dropped. A replacement containing
 *   embedded newlines becomes alternating `Text`/`Break(HARD)` segments ([replacementDoc]) rather
 *   than one opaque `Text`, so `Layout` derives each subsequent line's column from ambient indent
 *   depth;
 * - land inside a single `Doc.Text` leaf (always cleanly splittable) or a `Doc.Break` leaf
 *   (splittable only up to its [Doc.Break.literal]'s represented length; a cut strictly inside the
 *   elided trailing-indent tail is refused);
 * - be a zero-width insertion, threaded to the one leaf whose span starts at the insertion point
 *   (or, at true end-of-file, appended after everything).
 *
 * Returns `null` if any edit cannot be cleanly mapped this way.
 */
object DocSplicer {
    fun splice(doc: Doc, edits: List<WEdit>): Doc? {
        val (wrapped, consumed) = applyIndentScopes(doc, edits) ?: return null
        var current = wrapped
        for (edit in edits) {
            if (edit in consumed) continue
            current = spliceOne(current, edit) ?: return null
        }
        return current
    }

    /**
     * Wraps, in one [Doc.Indent] level, the span between each [IndentScope.OPEN] edit's own start
     * and its matching [IndentScope.CLOSE] edit's own start — a stack match over [edits] in their
     * already-span-sorted order. The wrapped span starts at the open edit's own start, since its
     * trailing break belongs inside the new scope and decides the following line's column.
     *
     * The matching close edit is left outside the new `Indent`, since its own break decides the
     * closing line's column at the outer depth. A non-zero-width close edit (replacing a real gap)
     * is left for the ordinary per-edit [spliceOne] pass. A zero-width close edit (nothing to its
     * right) is instead spliced here directly, as a new sibling appended immediately after the
     * wrapped `Indent` at the same tree level, and returned in this call's consumed set — the
     * generic insertion rule in [spliceOne] would otherwise thread it to the enclosing block's own
     * dedent whitespace, one level shallower than intended.
     *
     * Returns `null` if a `CLOSE` has no matching prior `OPEN`, an `OPEN` is never closed, or a
     * wrap's span cannot be found as an exact node/part boundary in [doc].
     */
    private fun applyIndentScopes(doc: Doc, edits: List<WEdit>): Pair<Doc, Set<WEdit>>? {
        var current = doc
        val opens = ArrayDeque<WEdit>()
        val consumed = mutableSetOf<WEdit>()
        for (edit in edits) {
            when (edit.indentScope) {
                IndentScope.OPEN -> opens.addLast(edit)
                IndentScope.CLOSE -> {
                    val open = opens.removeLastOrNull() ?: return null
                    current =
                        if (edit.startOffset == edit.endOffset) {
                            consumed.add(edit)
                            wrapIndent(current, open.startOffset, edit.startOffset, replacementDoc(edit))
                        } else {
                            wrapIndent(current, open.startOffset, edit.startOffset, after = null)
                        }
                        ?: return null
                }

                IndentScope.NONE -> {}
            }
        }
        return if (opens.isEmpty()) current to consumed else null
    }

    /**
     * Wraps the exact `[start, end)` span of [doc] in one [Doc.Indent]. When [after] is non-null (a
     * zero-width close edit, see [applyIndentScopes]), it is appended as a new sibling immediately
     * following the wrapped `Indent`, at the same structural level. Returns `null` if no
     * node/contiguous run of `Doc.Concat` parts has those bounds.
     */
    private fun wrapIndent(
        doc: Doc,
        start: Int,
        end: Int,
        after: Doc?,
    ): Doc? {
        if (doc.start == start && doc.end == end) {
            val wrapped = Doc.Indent(doc)
            return if (after == null) wrapped else Doc.Concat(listOf(wrapped, after), doc.start, after.end)
        }
        return when (doc) {
            is Doc.Concat -> {
                val firstIdx = doc.parts.indexOfFirst { it.start == start }
                val lastIdx = doc.parts.indexOfLast { it.end == end }
                if (firstIdx in 0..lastIdx) {
                    val wrapped = Doc.Indent(Doc.Concat(doc.parts.subList(firstIdx, lastIdx + 1), start, end))
                    val result = ArrayList<Doc>(doc.parts.size - (lastIdx - firstIdx) + 1)
                    result.addAll(doc.parts.subList(0, firstIdx))
                    result.add(wrapped)
                    if (after != null) result.add(after)
                    result.addAll(doc.parts.subList(lastIdx + 1, doc.parts.size))
                    Doc.Concat(result, doc.start, doc.end)
                } else {
                    val idx = doc.parts.indexOfFirst { it.start <= start && end <= it.end }
                    if (idx < 0) return null
                    val updated = wrapIndent(doc.parts[idx], start, end, after) ?: return null
                    val result = doc.parts.toMutableList()
                    result[idx] = updated
                    Doc.Concat(result, doc.start, doc.end)
                }
            }

            is Doc.Indent -> wrapIndent(doc.body, start, end, after)?.let { Doc.Indent(it) }
            is Doc.Group -> wrapIndent(doc.body, start, end, after)?.let { doc.withBody(it) }
            else -> null
        }
    }

    /**
     * Materializes an edit's replacement text as a `Doc`: a single [Doc.Text] when it has no
     * embedded newline, or alternating [Doc.Text]/`Doc.Break(HARD)` segments otherwise, so `Layout`
     * synthesizes each subsequent segment's leading indent from ambient depth — the mechanism a
     * minimal, unindented brace edit
     * ([com.varlanv.wrasse.rules.BraceInsertion.wrapEditsMinimal]) relies on.
     */
    private fun replacementDoc(edit: WEdit): Doc {
        val text = edit.replacement
        if (!text.contains('\n')) return Doc.Text(text, edit.startOffset, edit.endOffset)

        val parts = mutableListOf<Doc>()
        var from = 0
        while (true) {
            val newlineIdx = text.indexOf('\n', from)
            if (newlineIdx < 0) {
                if (from < text.length) parts.add(Doc.Text(text.substring(from), edit.startOffset, edit.endOffset))
                break
            }
            if (newlineIdx > from) {
                parts.add(Doc.Text(text.substring(from, newlineIdx), edit.startOffset, edit.endOffset))
            }
            parts.add(Doc.Break(BreakKind.HARD, "\n", start = edit.startOffset, end = edit.endOffset))
            from = newlineIdx + 1
        }
        return Doc.Concat(parts, edit.startOffset, edit.endOffset)
    }

    /**
     * A [GroupKind.FLUID] group hangs its value under the `=` whose trailing break opens its body;
     * an edit that consumes that break (a block-body conversion replacing `= `) leaves nothing to
     * hang from, so the group falls back to a plain one at ambient depth.
     */
    private fun lostLeadingBreak(original: Doc, spliced: Doc): Boolean =
        original is Doc.Concat &&
            original.parts.firstOrNull() is Doc.Break &&
            spliced is Doc.Concat &&
            spliced.parts.firstOrNull() !is Doc.Break

    private fun spliceOne(doc: Doc, edit: WEdit): Doc? {
        if (edit.startOffset == edit.endOffset && edit.startOffset >= doc.end) {
            return Doc.Concat(listOf(doc, replacementDoc(edit)), doc.start, edit.endOffset)
        }

        var emitted = false

        fun emitReplacementOnce(): Doc? = if (!emitted) {
            emitted = true
            replacementDoc(edit)
        } else {
            null
        }

        fun splitText(node: Doc.Text): Doc {
            val len = node.value.length
            val localStart = (edit.startOffset - node.start).coerceIn(0, len)
            val localEnd = (edit.endOffset - node.start).coerceIn(0, len)
            val parts = mutableListOf<Doc>()
            if (localStart > 0) {
                parts.add(Doc.Text(node.value.subSequence(0, localStart), node.start, node.start + localStart))
            }
            emitReplacementOnce()?.let { parts.add(it) }
            if (localEnd < len) {
                parts.add(Doc.Text(node.value.subSequence(localEnd, len), node.start + localEnd, node.end))
            }
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
                prefix = Doc.Break(node.kind, node.literal.subSequence(0, local), node.flat, node.start, cut)
            }
            if (edit.endOffset < node.end) {
                val cut = edit.endOffset
                if (cut in (representedEnd + 1) until node.end) return null
                val local = cut - node.start
                if (local < node.literal.length) {
                    suffix =
                        Doc.Break(
                            node.kind,
                            node.literal.subSequence(local, node.literal.length),
                            node.flat,
                            cut,
                            node.end,
                        )
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
                is Doc.TrailingComma -> null
                is Doc.Indent -> rec(node.body)?.let { Doc.Indent(it) }
                is Doc.Group -> rec(node.body)?.let { body ->
                    if (node.kind == GroupKind.FLUID && lostLeadingBreak(node.body, body)) {
                        Doc.Group(body)
                    } else {
                        node.withBody(body)
                    }
                }
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

// expect-clean
// fixture-option: trailing-newline
