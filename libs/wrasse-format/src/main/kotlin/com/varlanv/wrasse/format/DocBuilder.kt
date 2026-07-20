package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val INDENTING_TYPES = setOf(WNodeType.BLOCK, WNodeType.CLASS_BODY, WNodeType.WHEN, WNodeType.FUNCTION_LITERAL)
private val CHAIN_LINK_TYPES = setOf(WNodeType.DOT_QUALIFIED_EXPRESSION, WNodeType.SAFE_ACCESS_EXPRESSION)

/**
 * The privileged stream consumer that turns the SAX walk into a [Doc] tree, one `when (ctx.type)`
 * decision at a time (§5.3). Registered alongside ordinary rules (see `WrassePlugin`'s `alwaysOn`
 * list) so it rides the same single walk; never user-configurable via `wrasse.json`'s `rules` map
 * (`format` is its own on/off key, see [WFormatConfig]).
 *
 * Every leaf becomes [Doc.Text] verbatim, **except** [WNodeType.WHITE_SPACE] tokens that contain a
 * newline, which become a `HARD` [Doc.Break] by default: the original text up to and including its
 * final `\n` is kept exactly, and [Layout] synthesizes the indent for the line that follows from the
 * ambient [Doc.Indent] depth. Every real newline is preserved verbatim this way *except* at the
 * handful of legal break points [resolveChainFrame]/[resolveBinaryFrame]/[resolveArgumentListFrame]
 * claim, where the adjacent whitespace becomes a `SOFT` break inside a [Doc.Group] instead — the
 * printer, not the source, now decides whether that line joins or stays split.
 *
 * A node's direct children are buffered until [exitNode], because a node's own layout (indent scope,
 * chain flattening, operator placement, argument wrapping) can only be decided once its children are
 * complete — see [resolveFrame] and its per-construct helpers.
 */
class DocBuilder(
    formatConfig: WFormatConfig,
) : WStreamRule {

    override val id: String = "format"
    override val config: WrasseRuleConfig = formatConfig.ruleConfig

    private val style = formatConfig.style
    private val frames = ArrayDeque<Frame>()
    private var rootDoc: Doc = Doc.Concat(emptyList())

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val text = ctx.leafText?.toString() ?: ""
        val entry = if (ctx.type == WNodeType.WHITE_SPACE && text.contains('\n')) {
            ChildEntry.Ws(text, ctx.startOffset)
        } else {
            ChildEntry.Resolved(ctx.type, Doc.Text(text, ctx.startOffset, ctx.endOffset))
        }
        frames.last().children.add(entry)
    }

    override fun enterNode(ctx: WContext) {
        frames.addLast(Frame(ctx.type))
    }

    override fun exitNode(ctx: WContext) {
        val frame = frames.removeLast()
        val parentType = frames.lastOrNull()?.type
        val doc = resolveFrame(frame, ctx.startOffset, ctx.endOffset, parentType)
        if (frames.isEmpty()) {
            rootDoc = doc
        } else {
            frames.last().children.add(ChildEntry.Resolved(ctx.type, doc))
        }
    }

    /**
     * Splices every edit still in [WContext.editPlan] into [rootDoc] (§5.3's "content → layout"
     * contract), then renders. Called explicitly by the host, once, only after every other rule —
     * including a deferred [com.varlanv.wrasse.model.WFileRule] — has made its own final
     * contribution to the plan (never from [afterFile], which the walk calls too early for that
     * guarantee to hold). [DocSplicer.splice] returning `null` means at least one edit could not
     * be cleanly mapped onto a `Doc` leaf; per §5.3, that is never guessed at — this file's format
     * pass is skipped entirely for this compile (no report, no edit) and the plan is handed back
     * untouched so the declining rules' own edits still reach the patch.
     */
    fun finish(ctx: WContext, reporter: WReporter) {
        val original = ctx.sourceText.toString()
        val taken = ctx.editPlan.takeAll()
        val spliced = DocSplicer.splice(rootDoc, taken.map { it.edit })
        if (spliced == null) {
            ctx.editPlan.restore(taken)
            return
        }
        val rendered = Layout.render(spliced, style)
        if (rendered == original) return
        reporter.report(
            id,
            "File is not wrasse-formatted",
            0,
            original.length,
            this,
            edits = listOf(WEdit(0, original.length, rendered)),
        )
    }

    /**
     * Dispatches a completed node's buffered children to the one decision that owns its layout:
     * chain flattening for a dot/safe-access link ([resolveChainFrame]), operator placement for a
     * binary expression ([resolveBinaryFrame]), argument wrapping for a call's argument list
     * ([resolveArgumentListFrame]), or the brace/indent-scope handling every other node shares
     * ([resolveBraceFrame]).
     */
    private fun resolveFrame(frame: Frame, start: Int, end: Int, parentType: WNodeType?): Doc = when (frame.type) {
        WNodeType.DOT_QUALIFIED_EXPRESSION, WNodeType.SAFE_ACCESS_EXPRESSION ->
            resolveChainFrame(frame, start, end, isRoot = parentType !in CHAIN_LINK_TYPES)

        WNodeType.BINARY_EXPRESSION ->
            resolveBinaryFrame(frame, start, end, isRoot = parentType != WNodeType.BINARY_EXPRESSION)

        WNodeType.VALUE_ARGUMENT_LIST -> resolveArgumentListFrame(frame, start, end)

        else -> resolveBraceFrame(frame, start, end)
    }

    /**
     * A node in [INDENTING_TYPES] whose own last child is not literally `RBRACE` owns no closing
     * delimiter of its own and is a transparent pass-through instead — the case that distinguishes a
     * real, braced `BLOCK` (a function/if/loop body, which owns its own `{`/`}`) from a lambda
     * body's `BLOCK` (whose surrounding `{`, whitespace, and `}` belong to the enclosing
     * `FUNCTION_LITERAL`, not to the statement-sequence `BLOCK` nested inside it — confirmed
     * empirically off a real LightTree dump, not assumed). Without this check, a multi-statement
     * lambda body would be indented twice (once by `FUNCTION_LITERAL`, again by its interior
     * `BLOCK`) while a single-statement one would accidentally look right, masking the bug.
     */
    private fun resolveBraceFrame(frame: Frame, start: Int, end: Int): Doc {
        val children = frame.children
        if (children.isEmpty()) return Doc.Concat(emptyList(), start, end)

        val lastIndex = children.size - 1
        val opensIndentScope = frame.type in INDENTING_TYPES && children[lastIndex].type == WNodeType.RBRACE
        if (!opensIndentScope) {
            return Doc.Concat(children.map { resolveEntry(it) }, start, end)
        }

        val dedentIndex = lastIndex - 1
        val hasDedent = dedentIndex >= 0 && children[dedentIndex] is ChildEntry.Ws

        val innerCount = if (hasDedent) dedentIndex else lastIndex
        val innerParts = ArrayList<Doc>(innerCount)
        for (i in 0 until innerCount) {
            innerParts.add(resolveEntry(children[i]))
        }

        val innerStart = innerParts.firstOrNull()?.start ?: start
        val innerEnd = innerParts.lastOrNull()?.end ?: start
        val parts = mutableListOf<Doc>(Doc.Indent(Doc.Concat(innerParts, innerStart, innerEnd)))
        if (hasDedent) {
            parts.add(resolveEntry(children[dedentIndex]))
        }
        parts.add(resolveEntry(children[lastIndex]))
        return Doc.Concat(parts, start, end)
    }

    /**
     * A dot/safe-access chain is built bottom-up: `a.b().c()` is a [WNodeType.DOT_QUALIFIED_EXPRESSION]
     * whose receiver is itself the (already-resolved) `a.b()` link. Only the outermost link — the one
     * whose parent is not itself a chain-link type ([isRoot]) — wraps the whole flattened chain in one
     * [Doc.Group]/[Doc.Indent]; every inner link just splices its own dot/safe-access `SOFT` break into
     * the flat body it hands up, so the whole chain shares a single continuation-indent level instead of
     * nesting one deeper per link. The break sits *before* the operator (ktlint's `chain-wrapping`
     * ground truth: a line must not end with `.`/`?.`), flush against the receiver in flat form.
     */
    private fun resolveChainFrame(frame: Frame, start: Int, end: Int, isRoot: Boolean): Doc {
        val children = frame.children
        val opIdx = children.indexOfFirst { it.type == WNodeType.DOT || it.type == WNodeType.SAFE_ACCESS }
        if (opIdx < 0) return Doc.Concat(children.map { resolveEntry(it) }, start, end)

        val parts = spliceBreak(children, anchorIndex = opIdx, breakBefore = true, flat = "")
        return if (isRoot) wrapRoot(parts, start, end) else Doc.Concat(parts, start, end)
    }

    /**
     * Nested [WNodeType.BINARY_EXPRESSION]s (`a + b + c`) flatten the same way [resolveChainFrame]
     * does: only the outermost expression wraps in [Doc.Group]/[Doc.Indent]. The break sits *after*
     * the operator for every operator but `?:` (ktlint's `chain-wrapping`/`binary-expression-wrapping`
     * ground truth: arithmetic/logical/comparison operators stay at the end of the line they came
     * from; only elvis moves to the start of the next line, alongside `.`/`?.`).
     */
    private fun resolveBinaryFrame(frame: Frame, start: Int, end: Int, isRoot: Boolean): Doc {
        val children = frame.children
        val opIdx = children.indexOfFirst { it.type == WNodeType.OPERATION_REFERENCE }
        if (opIdx < 0) return Doc.Concat(children.map { resolveEntry(it) }, start, end)

        val isElvis = flatText((children[opIdx] as ChildEntry.Resolved).doc) == "?:"
        val parts = spliceBreak(children, anchorIndex = opIdx, breakBefore = isElvis, flat = " ")
        return if (isRoot) wrapRoot(parts, start, end) else Doc.Concat(parts, start, end)
    }

    /**
     * Wraps a root chain/binary expression's flattened [parts] in [Doc.Group]/[Doc.Indent] for the
     * fit-check and continuation indent — *except* for a trailing part that can never render on one
     * line by itself (a call's trailing multi-statement lambda argument, the only such shape these two
     * constructs can carry): that part, and everything after it, sits outside both the [Doc.Group]
     * (so its unavoidable break never poisons the fit-check for what precedes it — the printer still
     * needs to decide `receiver.method {` fits before an inherently-multi-line lambda body) and the
     * [Doc.Indent] (so the lambda body keeps rendering at its own, pre-existing ambient depth exactly
     * as [resolveBraceFrame] already gives it, rather than one level deeper for no reason).
     */
    private fun wrapRoot(parts: List<Doc>, start: Int, end: Int): Doc {
        val splitIdx = parts.indexOfFirst { containsForcedBreak(it) }
        if (splitIdx < 0) return Doc.Group(Doc.Indent(Doc.Concat(parts, start, end)))

        val headEnd = parts.getOrNull(splitIdx - 1)?.end ?: start
        val head = Doc.Indent(Doc.Group(Doc.Concat(parts.subList(0, splitIdx), start, headEnd)))
        val tail = Doc.Concat(parts.subList(splitIdx, parts.size), headEnd, end)
        return Doc.Concat(listOf(head, tail), start, end)
    }

    private fun containsForcedBreak(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> doc.value.contains('\n')
        is Doc.Break -> doc.kind == BreakKind.HARD
        is Doc.Indent -> containsForcedBreak(doc.body)
        is Doc.Group -> containsForcedBreak(doc.body)
        is Doc.Concat -> doc.parts.any { containsForcedBreak(it) }
    }

    /**
     * A call's argument list wraps in its own [Doc.Group]/[Doc.Indent], independent of any chain or
     * binary expression it sits inside — nested groups fit-check independently ([Layout]), so a short
     * argument list inside a long broken chain can still render flat. Break points: right after `(`,
     * right after every comma that has another argument following it, and right before `)`. A comma
     * already followed by nothing but whitespace (a pre-existing trailing comma) gets no break of its
     * own — the closing break already lands right after it, so doubling up would print a blank line.
     * Trailing-comma *insertion* is out of this slice's scope; an existing one is preserved as-is.
     */
    private fun resolveArgumentListFrame(frame: Frame, start: Int, end: Int): Doc {
        val children = frame.children
        val lparIdx = children.indexOfFirst { it.type == WNodeType.LPAR }
        val rparIdx = children.indexOfLast { it.type == WNodeType.RPAR }
        if (lparIdx < 0 || rparIdx < 0 || rparIdx <= lparIdx) {
            return Doc.Concat(children.map { resolveEntry(it) }, start, end)
        }
        val hasContent = (lparIdx + 1 until rparIdx).any { !it.isWs(children) }
        if (!hasContent) {
            return Doc.Concat(children.map { resolveEntry(it) }, start, end)
        }

        val lparDoc = resolveEntry(children[lparIdx])
        val rparDoc = resolveEntry(children[rparIdx])

        val interior = ArrayList<Doc>()
        interior.add(wsBreakAt(children, lparIdx + 1, lparDoc.end, flat = ""))
        var i = lparIdx + 1
        while (i < rparIdx) {
            val entry = children[i]
            if (entry.type == WNodeType.WHITE_SPACE) {
                i++
                continue
            }
            val entryDoc = resolveEntry(entry)
            interior.add(entryDoc)
            if (entry.type == WNodeType.COMMA && hasNonWsBetween(children, i + 1, rparIdx)) {
                interior.add(wsBreakAt(children, i + 1, entryDoc.end, flat = " "))
            }
            i++
        }

        val interiorDoc = Doc.Concat(interior, interior.first().start, interior.last().end)
        val closingBreak = wsBreakAt(children, rparIdx - 1, rparDoc.start, flat = "")
        return Doc.Group(Doc.Concat(listOf(lparDoc, Doc.Indent(interiorDoc), closingBreak, rparDoc), start, end))
    }

    private fun Int.isWs(children: List<ChildEntry>): Boolean = children[this].type == WNodeType.WHITE_SPACE

    private fun hasNonWsBetween(children: List<ChildEntry>, from: Int, until: Int): Boolean =
        (from until until).any { children[it].type != WNodeType.WHITE_SPACE }

    /**
     * Builds [children] with one `SOFT` [Doc.Break] spliced in at [anchorIndex] ([breakBefore] it or
     * after it), consuming the adjacent whitespace child in its place if one is there — whether or
     * not that whitespace happens to carry a newline, since a currently-flat construct's gap is an
     * ordinary [ChildEntry.Resolved] `WHITE_SPACE`, not a [ChildEntry.Ws]. Shared by
     * [resolveChainFrame] (break before the dot/safe-access operator) and [resolveBinaryFrame] (break
     * after the operator, or before it for `?:`).
     */
    private fun spliceBreak(children: List<ChildEntry>, anchorIndex: Int, breakBefore: Boolean, flat: String): List<Doc> {
        val wsIndex = if (breakBefore) anchorIndex - 1 else anchorIndex + 1
        val hasWs = wsIndex in children.indices && wsIndex.isWs(children)
        val insertIndex = if (breakBefore) anchorIndex else anchorIndex + 1
        val anchorDoc = (children[anchorIndex] as ChildEntry.Resolved).doc
        val fallback = if (breakBefore) anchorDoc.start else anchorDoc.end
        val breakDoc = wsBreakAt(children, wsIndex, fallback, flat)

        val parts = ArrayList<Doc>(children.size + 1)
        for (idx in children.indices) {
            if (idx == insertIndex) parts.add(breakDoc)
            if (!(hasWs && idx == wsIndex)) parts.add(resolveEntry(children[idx]))
        }
        if (insertIndex == children.size) parts.add(breakDoc)
        return parts
    }

    /**
     * A `SOFT` [Doc.Break] at [wsIndex]: if [children] has a `WHITE_SPACE`-typed child there ([Ws] or
     * an ordinary [Resolved] with no embedded newline), the break's span claims exactly the whitespace
     * it replaces; otherwise (no source whitespace at that boundary at all) a zero-width break is
     * anchored at [fallback], letting [Layout] insert or omit it purely from the group's fit decision.
     * [flat] always wins over whatever the replaced whitespace actually looked like — the printer, not
     * the source, now owns this gap's spacing.
     */
    private fun wsBreakAt(children: List<ChildEntry>, wsIndex: Int, fallback: Int, flat: String): Doc.Break {
        val entry = children.getOrNull(wsIndex)
        return if (entry != null && entry.type == WNodeType.WHITE_SPACE) {
            val (spanStart, spanEnd) = when (entry) {
                is ChildEntry.Ws -> entry.start to entry.start + entry.rawText.length
                is ChildEntry.Resolved -> entry.doc.start to entry.doc.end
            }
            Doc.Break(BreakKind.SOFT, flat = flat, start = spanStart, end = spanEnd)
        } else {
            Doc.Break(BreakKind.SOFT, flat = flat, start = fallback, end = fallback)
        }
    }

    private fun flatText(doc: Doc): String = when (doc) {
        is Doc.Text -> doc.value
        is Doc.Concat -> doc.parts.joinToString("") { flatText(it) }
        else -> ""
    }

    private fun resolveEntry(entry: ChildEntry): Doc = when (entry) {
        is ChildEntry.Resolved -> entry.doc
        is ChildEntry.Ws -> {
            val literal = entry.rawText.substring(0, entry.rawText.lastIndexOf('\n') + 1)
            Doc.Break(BreakKind.HARD, literal = literal, start = entry.start, end = entry.start + entry.rawText.length)
        }
    }

    private class Frame(val type: WNodeType) {
        val children = mutableListOf<ChildEntry>()
    }

    private sealed interface ChildEntry {
        val type: WNodeType

        class Resolved(override val type: WNodeType, val doc: Doc) : ChildEntry
        class Ws(val rawText: String, val start: Int) : ChildEntry {
            override val type: WNodeType = WNodeType.WHITE_SPACE
        }
    }
}
