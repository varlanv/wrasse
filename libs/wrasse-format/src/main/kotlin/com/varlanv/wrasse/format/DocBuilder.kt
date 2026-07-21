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
private val COMMENT_TYPES = setOf(WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT)
private val SUPER_TYPE_ENTRY_TYPES = setOf(WNodeType.SUPER_TYPE_ENTRY, WNodeType.SUPER_TYPE_CALL_ENTRY)
private val ANNOTATION_CONTAINER_TYPES = setOf(WNodeType.MODIFIER_LIST, WNodeType.ANNOTATED_EXPRESSION)
private val ANNOTATION_EXEMPT_PARENT_TYPES = setOf(WNodeType.VALUE_PARAMETER, WNodeType.VALUE_ARGUMENT)

private val KEYWORDS_WANTING_SPACE_AFTER =
    setOf(WNodeType.KW_IF, WNodeType.KW_WHEN, WNodeType.KW_FOR, WNodeType.KW_WHILE, WNodeType.KW_CATCH, WNodeType.KW_WHERE)
private val CLOSERS_NOT_NEEDING_SPACE_AFTER_COMMA =
    setOf(WNodeType.RPAR, WNodeType.RBRACKET, WNodeType.GT, WNodeType.RBRACE)
private val COLON_WANTS_SPACE_BOTH_SIDES =
    setOf(
        WNodeType.CLASS,
        WNodeType.OBJECT_DECLARATION,
        WNodeType.OBJECT_LITERAL,
        WNodeType.SECONDARY_CONSTRUCTOR,
        WNodeType.TYPE_PARAMETER,
        WNodeType.TYPE_CONSTRAINT,
    )

/**
 * The privileged stream consumer that turns the SAX walk into a [Doc] tree, one `when (ctx.type)`
 * decision at a time. Registered alongside ordinary rules so it rides the same single walk; never
 * user-configurable via `wrasse.json`'s `rules` map (`format` is its own on/off key, see
 * [WFormatConfig]).
 *
 * Every leaf becomes [Doc.Text] verbatim, except a [WNodeType.WHITE_SPACE] token containing a
 * newline, which becomes a `HARD` [Doc.Break]: the original text up to and including its final
 * `\n` is kept exactly, and [Layout] synthesizes the indent for the following line from the
 * ambient [Doc.Indent] depth. [resolveChainFrame]/[resolveBinaryFrame]/[resolveArgumentListFrame]/
 * [resolveValueParameterListFrame] replace the adjacent whitespace with a `SOFT` break inside a
 * [Doc.Group] instead, so the printer decides whether that line joins or stays split.
 *
 * A node's direct children are buffered until [exitNode], since a node's own layout can only be
 * decided once its children are complete — see [resolveFrame] and its per-construct helpers.
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
     * Splices every edit still in [WContext.editPlan] into [rootDoc], then renders. Must be called
     * exactly once, after the walk's `afterFile` phase and its deferred
     * [com.varlanv.wrasse.model.WFileRule] phase have both completed, so every rule's final edit is
     * already in the plan. [DocSplicer.splice] returning `null` means at least one edit could not
     * be cleanly mapped onto a `Doc` leaf: the format pass is skipped for this compile (no report,
     * no edit) and the plan is handed back untouched so the declining rules' own edits still reach
     * the patch.
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
     * ([resolveArgumentListFrame]), parameter wrapping for a function's or primary constructor's
     * own parameter list ([resolveValueParameterListFrame]), the trailing-comma-only decision for
     * a generic list ([resolveAngleListFrame]), a destructuring declaration
     * ([resolveDestructuringFrame]), a `when`-entry's own condition list ([resolveWhenEntryFrame]),
     * a class's own supertype list ([resolveSuperTypeListFrame]), an annotation container
     * ([resolveAnnotationContainerFrame]), or the brace/indent-scope handling every other node
     * shares ([resolveBraceFrame]).
     */
    private fun resolveFrame(frame: Frame, start: Int, end: Int, parentType: WNodeType?): Doc = when (frame.type) {
        WNodeType.DOT_QUALIFIED_EXPRESSION, WNodeType.SAFE_ACCESS_EXPRESSION ->
            resolveChainFrame(frame, start, end, isRoot = parentType !in CHAIN_LINK_TYPES)

        WNodeType.BINARY_EXPRESSION ->
            resolveBinaryFrame(frame, start, end, isRoot = parentType != WNodeType.BINARY_EXPRESSION)

        WNodeType.VALUE_ARGUMENT_LIST -> resolveArgumentListFrame(frame, start, end)

        WNodeType.VALUE_PARAMETER_LIST -> resolveValueParameterListFrame(frame, start, end, parentType)

        WNodeType.TYPE_PARAMETER_LIST, WNodeType.TYPE_ARGUMENT_LIST -> resolveAngleListFrame(frame, start, end)

        WNodeType.DESTRUCTURING_DECLARATION -> resolveDestructuringFrame(frame, start, end)

        WNodeType.WHEN_ENTRY -> resolveWhenEntryFrame(frame, start, end)

        WNodeType.SUPER_TYPE_LIST -> resolveSuperTypeListFrame(frame, start, end, parentType)

        WNodeType.MODIFIER_LIST, WNodeType.ANNOTATED_EXPRESSION -> resolveAnnotationContainerFrame(frame, start, end, parentType)

        WNodeType.PREFIX_EXPRESSION, WNodeType.POSTFIX_EXPRESSION -> resolveUnaryFrame(frame, start, end)

        else -> resolveBraceFrame(frame, start, end)
    }

    /**
     * A node in [INDENTING_TYPES] wraps its interior in one [Doc.Indent] and dedents the line
     * holding its own closing `RBRACE`, but only when its own last child is literally `RBRACE` — a
     * lambda body's `BLOCK` has no `{`/`}` of its own (those belong to the enclosing
     * `FUNCTION_LITERAL`) and is a transparent pass-through instead.
     */
    private fun resolveBraceFrame(frame: Frame, start: Int, end: Int): Doc {
        val rawChildren = if (frame.type == WNodeType.FUNCTION_LITERAL) normalizeLambdaBraces(frame.children) else frame.children
        val children = adjustAnnotationTrailingGap(rawChildren)
        if (children.isEmpty()) return Doc.Concat(emptyList(), start, end)

        val suppressSuperTypeListLeadGap = frame.ownsSuperTypeListLeadGap
        val lastIndex = children.size - 1
        val opensIndentScope = frame.type in INDENTING_TYPES && children[lastIndex].type == WNodeType.RBRACE
        if (!opensIndentScope) {
            return Doc.Concat(
                normalizeChildren(children, frame.type, ancestorHasFun(frame.type), suppressSuperTypeListLeadGap),
                start,
                end,
            )
        }

        val dedentIndex = lastIndex - 1
        val hasDedent = dedentIndex >= 0 && children[dedentIndex] is ChildEntry.Ws

        val innerCount = if (hasDedent) dedentIndex else lastIndex
        val innerParts = normalizeChildren(
            children.subList(0, innerCount),
            frame.type,
            ancestorHasFun(frame.type),
            suppressSuperTypeListLeadGap,
        )

        val innerStart = innerParts.firstOrNull()?.start ?: start
        val innerEnd = innerParts.lastOrNull()?.end ?: start
        val parts = mutableListOf<Doc>(Doc.Indent(Doc.Concat(innerParts, innerStart, innerEnd)))
        if (hasDedent) {
            parts.add(clampWs(children[dedentIndex] as ChildEntry.Ws, newlineCount = 1))
        }
        parts.add(resolveEntry(children[lastIndex]))
        return Doc.Concat(parts, start, end)
    }

    /**
     * Whether any still-open enclosing frame is a [WNodeType.FUN] — an unbounded ancestor walk,
     * not just the direct parent.
     */
    private fun ancestorHasFun(frameType: WNodeType): Boolean =
        frameType == WNodeType.BLOCK && frames.any { it.type == WNodeType.FUN }

    /**
     * A [WNodeType.PREFIX_EXPRESSION]/[WNodeType.POSTFIX_EXPRESSION] (`-x`, `!x`, `x++`, `x!!`) is
     * always tight to its operand: every single-line whitespace child inside one of these two
     * frames collapses to nothing.
     */
    private fun resolveUnaryFrame(frame: Frame, start: Int, end: Int): Doc {
        val parts = frame.children.map { entry ->
            if (isPlainWhitespace(entry)) {
                val ws = (entry as ChildEntry.Resolved).doc
                Doc.Text("", ws.start, ws.end)
            } else {
                resolveEntry(entry)
            }
        }
        return Doc.Concat(parts, start, end)
    }

    /**
     * `{`/`}` spacing for a lambda body (`{ x -> ... }`): the gap right after the opening `{` and
     * right before the closing `}` collapse to no space when nothing but whitespace sits between
     * the lambda's own braces and its content, or to exactly one space otherwise. A genuine
     * newline there (multi-line body) is left untouched.
     */
    private fun normalizeLambdaBraces(children: List<ChildEntry>): List<ChildEntry> {
        if (children.size < 2) return children
        return normalizeLambdaTail(normalizeLambdaHead(children))
    }

    /**
     * Whether [entry] is `RBRACE`/`LBRACE` or a resolved `Doc` with no rendered content
     * ([isEmptyDoc]) — a lambda's body is always a [WNodeType.BLOCK] child, even when empty
     * (`names.forEach {}` is `[LBRACE, BLOCK(empty), RBRACE]`), so emptiness is decided from the
     * resolved `Doc`'s actual content, never from its source span.
     */
    private fun isEffectivelyEmpty(entry: ChildEntry?): Boolean =
        entry == null ||
            entry.type == WNodeType.RBRACE ||
            entry.type == WNodeType.LBRACE ||
            (entry is ChildEntry.Resolved && isEmptyDoc(entry.doc))

    private fun isEmptyDoc(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> doc.value.isEmpty()
        is Doc.Concat -> doc.parts.all { isEmptyDoc(it) }
        is Doc.Indent -> isEmptyDoc(doc.body)
        is Doc.Group -> isEmptyDoc(doc.body)
        is Doc.Break -> false
        is Doc.TrailingComma -> false
    }

    private fun normalizeLambdaHead(children: List<ChildEntry>): List<ChildEntry> {
        val gap = children.getOrNull(1) ?: return children
        if (gap is ChildEntry.Ws) return children
        val gapIsWs = isPlainWhitespace(gap)
        val isEmpty = isEffectivelyEmpty(children.getOrNull(if (gapIsWs) 2 else 1))
        val desired = if (isEmpty) "" else " "
        return when {
            gapIsWs -> {
                val ws = (gap as ChildEntry.Resolved).doc
                children.toMutableList().also { it[1] = ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(desired, ws.start, ws.end)) }
            }

            desired == " " -> {
                val pos = (children[0] as ChildEntry.Resolved).doc.end
                children.toMutableList().also { it.add(1, ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(" ", pos, pos))) }
            }

            else -> children
        }
    }

    private fun normalizeLambdaTail(children: List<ChildEntry>): List<ChildEntry> {
        val lastIndex = children.size - 1
        val gapIndex = lastIndex - 1
        if (gapIndex < 0) return children
        val gap = children[gapIndex]
        if (gap is ChildEntry.Ws) return children
        val gapIsWs = isPlainWhitespace(gap)
        val isEmpty = if (gapIsWs) isEffectivelyEmpty(children.getOrNull(gapIndex - 1)) else isEffectivelyEmpty(gap)
        val desired = if (isEmpty) "" else " "
        return when {
            gapIsWs -> {
                val ws = (gap as ChildEntry.Resolved).doc
                children.toMutableList().also { it[gapIndex] = ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(desired, ws.start, ws.end)) }
            }

            desired == " " -> {
                val pos = (children[lastIndex] as ChildEntry.Resolved).doc.start
                children.toMutableList().also { it.add(lastIndex, ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(" ", pos, pos))) }
            }

            else -> children
        }
    }

    /**
     * The single choke point for horizontal-spacing normalization on every frame [resolveFrame]
     * doesn't already give a dedicated `Group`/break treatment to. For every gap between two
     * direct children — an actual single-line [WNodeType.WHITE_SPACE] child, or no child at all
     * (two tokens directly adjacent) — [spacingDecision] is asked for the correct rendering; `null`
     * preserves whatever was there verbatim. A real newline ([ChildEntry.Ws]) is never touched for
     * horizontal spacing here — its newline count is normalized separately by
     * [verticalGapNewlineCount]/[clampWs].
     */
    private fun normalizeChildren(
        children: List<ChildEntry>,
        frameType: WNodeType,
        ancestorHasFun: Boolean = false,
        suppressSuperTypeListLeadGap: Boolean = false,
    ): List<Doc> {
        val out = ArrayList<Doc>(children.size + 2)
        for (i in children.indices) {
            val entry = children[i]
            val nextIsSuperTypeList = suppressSuperTypeListLeadGap &&
                children.getOrNull(i - 1)?.type == WNodeType.COLON &&
                children.getOrNull(i + 1)?.type == WNodeType.SUPER_TYPE_LIST
            if (entry is ChildEntry.Ws) {
                if (nextIsSuperTypeList) {
                    out.add(Doc.Text("", entry.start, entry.start + entry.rawText.length))
                    continue
                }
                val prevEntry = children.getOrNull(i - 1)
                val nextEntry = children.getOrNull(i + 1)
                val isFirstAfterLbrace = i == 1 && prevEntry?.type == WNodeType.LBRACE
                val newlineCount = verticalGapNewlineCount(
                    frameType, prevEntry, nextEntry, isFirstAfterLbrace, ancestorHasFun,
                    actual = entry.rawText.count { it == '\n' },
                )
                out.add(clampWs(entry, newlineCount))
                continue
            }
            if (isPlainWhitespace(entry)) {
                if (nextIsSuperTypeList) {
                    val ws = (entry as ChildEntry.Resolved).doc
                    out.add(Doc.Text("", ws.start, ws.end))
                    continue
                }
                val prevType = children.getOrNull(i - 1)?.type
                val nextType = children.getOrNull(i + 1)?.type
                val decision = if (prevType != null && nextType != null) spacingDecision(frameType, prevType, nextType) else null
                val ws = (entry as ChildEntry.Resolved).doc
                out.add(if (decision != null) Doc.Text(decision, ws.start, ws.end) else ws)
                continue
            }
            out.add(resolveEntry(entry))
            val next = children.getOrNull(i + 1)
            if (next != null && !isPlainWhitespace(next) && next !is ChildEntry.Ws) {
                val isSuperTypeListLead = suppressSuperTypeListLeadGap &&
                    entry.type == WNodeType.COLON &&
                    next.type == WNodeType.SUPER_TYPE_LIST
                if (!isSuperTypeListLead && spacingDecision(frameType, entry.type, next.type) == " ") {
                    val pos = out.last().end
                    out.add(Doc.Text(" ", pos, pos))
                }
            }
        }
        return out
    }

    /**
     * Decides the exact newline count for a whitespace gap: at most one blank line anywhere
     * (handled by the final fallback below), except zero blank lines right before a `BLOCK`/
     * `CLASS_BODY`/`WHEN`/`FUNCTION_LITERAL`'s own closing `}` (handled directly at
     * [resolveBraceFrame]'s own dedent call site, not here), zero blank lines immediately after a
     * [WNodeType.CLASS_BODY]'s own `{` or a [WNodeType.BLOCK]'s own `{` when some enclosing frame
     * is a [WNodeType.FUN] ([ancestorHasFun]), and zero blank lines between a class name and its
     * primary constructor; exactly one blank line — the only case that can add a newline, not just
     * cap one, gated on [entryHasContent] — between a non-empty package directive and a non-empty
     * import list, and between that import list and whatever follows it.
     */
    private fun verticalGapNewlineCount(
        frameType: WNodeType,
        prevEntry: ChildEntry?,
        nextEntry: ChildEntry?,
        isFirstAfterLbrace: Boolean,
        ancestorHasFun: Boolean,
        actual: Int,
    ): Int {
        if (frameType == WNodeType.FILE) {
            if (prevEntry?.type == WNodeType.PACKAGE_DIRECTIVE && nextEntry?.type == WNodeType.IMPORT_LIST &&
                entryHasContent(prevEntry) && entryHasContent(nextEntry)
            ) {
                return 2
            }
            if (prevEntry?.type == WNodeType.IMPORT_LIST && nextEntry != null && entryHasContent(prevEntry)) {
                return 2
            }
        }
        if (isFirstAfterLbrace && (frameType == WNodeType.CLASS_BODY || (frameType == WNodeType.BLOCK && ancestorHasFun))) {
            return 1
        }
        if (frameType == WNodeType.CLASS && prevEntry?.type == WNodeType.IDENTIFIER && nextEntry?.type == WNodeType.PRIMARY_CONSTRUCTOR) {
            return 1
        }
        return if (actual > 2) 2 else actual
    }

    private fun entryHasContent(entry: ChildEntry?): Boolean = entry is ChildEntry.Resolved && !isEmptyDoc(entry.doc)

    /**
     * Renders [entry] as a `HARD` [Doc.Break] carrying exactly [newlineCount] newlines. When
     * [newlineCount] already matches the source's own count, the original literal (and any
     * trailing whitespace on its own blank lines) is reused verbatim, byte-identical to
     * [resolveEntry]'s default; otherwise a fresh `"\n".repeat(newlineCount)` is synthesized —
     * covering both directions, capping an over-long gap down *and* the one case
     * ([verticalGapNewlineCount]'s package/import rule) that adds a newline where the source had
     * none.
     */
    private fun clampWs(entry: ChildEntry.Ws, newlineCount: Int): Doc.Break {
        val end = entry.start + entry.rawText.length
        val actual = entry.rawText.count { it == '\n' }
        val literal = if (newlineCount == actual) {
            entry.rawText.substring(0, entry.rawText.lastIndexOf('\n') + 1)
        } else {
            "\n".repeat(newlineCount)
        }
        return Doc.Break(BreakKind.HARD, literal = literal, start = entry.start, end = end)
    }

    private fun isPlainWhitespace(entry: ChildEntry): Boolean =
        entry is ChildEntry.Resolved && entry.type == WNodeType.WHITE_SPACE

    /**
     * The horizontal-spacing table for a gap between [prevType] and [nextType] inside [frameType]:
     * no space before a comma, one space after (none before a closing delimiter); colon spacing
     * keyed on the enclosing declaration ([COLON_WANTS_SPACE_BOTH_SIDES] — one space both sides —
     * versus the default type-annotation colon — none before, one after — and no space at all for
     * an annotation use-site-target colon); one space after `if`/`when`/`for`/`while`/`catch` and
     * before `where` ([KEYWORDS_WANTING_SPACE_AFTER]); a spread operator's `*` tight to its
     * argument; no space just inside `(`/`)`/`[`/`]`; none between a name and its parameter or
     * argument list, except a `FUNCTION_TYPE`'s or a `FUNCTION_LITERAL`'s own parameter list (the
     * latter's gap from `{` is [normalizeLambdaHead]'s concern); no space just inside `<`/`>` when
     * the enclosing frame is a [WNodeType.TYPE_PARAMETER_LIST]/[WNodeType.TYPE_ARGUMENT_LIST];
     * `::` tight after always; `..`/`..<` tight both sides; no space before `?`.
     *
     * `null` means preserve whatever was there verbatim — every pair this table doesn't recognize.
     */
    private fun spacingDecision(frameType: WNodeType, prevType: WNodeType, nextType: WNodeType): String? {
        if (nextType == WNodeType.COMMA) return ""
        if (prevType == WNodeType.COMMA) {
            return if (nextType in CLOSERS_NOT_NEEDING_SPACE_AFTER_COMMA) "" else " "
        }

        if (nextType == WNodeType.COLON) {
            return if (frameType in COLON_WANTS_SPACE_BOTH_SIDES) " " else ""
        }
        if (prevType == WNodeType.COLON) {
            return if (frameType == WNodeType.ANNOTATION_ENTRY) "" else " "
        }

        if (prevType in KEYWORDS_WANTING_SPACE_AFTER) return " "
        if (nextType == WNodeType.KW_WHERE) return " "

        if (frameType == WNodeType.VALUE_ARGUMENT && prevType == WNodeType.MUL) return ""

        if (prevType == WNodeType.LPAR) return ""
        if (nextType == WNodeType.RPAR) return ""
        if ((nextType == WNodeType.VALUE_PARAMETER_LIST || nextType == WNodeType.VALUE_ARGUMENT_LIST) &&
            frameType != WNodeType.FUNCTION_TYPE && frameType != WNodeType.FUNCTION_LITERAL
        ) {
            return ""
        }

        if (prevType == WNodeType.LBRACKET) return ""
        if (nextType == WNodeType.RBRACKET) return ""

        if (frameType == WNodeType.TYPE_PARAMETER_LIST || frameType == WNodeType.TYPE_ARGUMENT_LIST) {
            if (prevType == WNodeType.LT) return ""
            if (nextType == WNodeType.GT) return ""
        }

        if (prevType == WNodeType.COLONCOLON) return ""

        if (nextType == WNodeType.RANGE || prevType == WNodeType.RANGE) return ""

        if (nextType == WNodeType.QUEST) return ""

        return null
    }

    /**
     * A dot/safe-access chain is built bottom-up: `a.b().c()` is a [WNodeType.DOT_QUALIFIED_EXPRESSION]
     * whose receiver is itself the already-resolved `a.b()` link. Only the outermost link ([isRoot]:
     * its parent is not itself a chain-link type) wraps the whole flattened chain in one
     * [Doc.Group]/[Doc.Indent]; every inner link splices its own dot/safe-access `SOFT` break into
     * the flat body it hands up, so the chain shares one continuation-indent level instead of
     * nesting one deeper per link. The break sits before the operator, flush against the receiver
     * in flat form.
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
     * does: only the outermost expression wraps in [Doc.Group]/[Doc.Indent]. The break sits after
     * the operator for every operator but `?:`, which breaks before it, alongside `.`/`?.`. The
     * flat-form gap is one space for every operator except the range operator (`..`), which is
     * tight both sides, unconditionally.
     */
    private fun resolveBinaryFrame(frame: Frame, start: Int, end: Int, isRoot: Boolean): Doc {
        val children = frame.children
        val opIdx = children.indexOfFirst { it.type == WNodeType.OPERATION_REFERENCE }
        if (opIdx < 0) return Doc.Concat(children.map { resolveEntry(it) }, start, end)

        val opText = flatText((children[opIdx] as ChildEntry.Resolved).doc)
        val isElvis = opText == "?:"
        val flat = if (opText == "..") "" else " "
        val parts = spliceBreak(children, anchorIndex = opIdx, breakBefore = isElvis, flat = flat)
        return if (isRoot) wrapRoot(parts, start, end) else Doc.Concat(parts, start, end)
    }

    /**
     * Wraps a root chain/binary expression's flattened [parts] in [Doc.Group]/[Doc.Indent] for the
     * fit-check and continuation indent, except for a trailing part with its own indent scope
     * ([hasOwnIndentScope], e.g. a trailing lambda argument's block body) and everything after it:
     * that tail sits outside both the [Doc.Group] — so its unavoidable break cannot force an
     * otherwise-joinable preceding part broken — and the [Doc.Indent] — so it keeps rendering at
     * the depth [resolveBraceFrame] already gives it. A part whose only forced break comes from a
     * plain multi-line [Doc.Text] (a raw string/KDoc token) has no indent scope of its own and
     * stays inside the shared `Group`/`Indent`; [Layout.flatWidth] still forces that group broken
     * independently.
     */
    private fun wrapRoot(parts: List<Doc>, start: Int, end: Int): Doc {
        val splitIdx = parts.indexOfFirst { hasOwnIndentScope(it) }
        if (splitIdx < 0) return Doc.Group(Doc.Indent(Doc.Concat(parts, start, end)))

        val headEnd = parts.getOrNull(splitIdx - 1)?.end ?: start
        val head = Doc.Indent(Doc.Group(Doc.Concat(parts.subList(0, splitIdx), start, headEnd)))
        val tail = Doc.Concat(parts.subList(splitIdx, parts.size), headEnd, end)
        return Doc.Concat(listOf(head, tail), start, end)
    }

    private fun hasOwnIndentScope(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> false
        is Doc.Break -> doc.kind == BreakKind.HARD
        is Doc.TrailingComma -> false
        is Doc.Indent -> hasOwnIndentScope(doc.body)
        is Doc.Group -> hasOwnIndentScope(doc.body)
        is Doc.Concat -> doc.parts.any { hasOwnIndentScope(it) }
    }

    /**
     * A call's argument list wraps in its own [Doc.Group]/[Doc.Indent], independent of any chain or
     * binary expression it sits inside: nested groups fit-check independently, so a short argument
     * list inside a long broken chain can still render flat. Break points: right after `(`, right
     * after every comma with another argument following it, and right before `)`. A pre-existing
     * trailing comma gets no break of its own, since the closing break already lands right after
     * it; its own text is replaced by [addDynamicTrailingComma]'s [Doc.TrailingComma], so its
     * presence in the rendered output follows this same [Doc.Group]'s own broken-vs-flat choice
     * rather than the source.
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
        val trailingCommaIdx = trailingCommaIndex(children, lparIdx + 1, rparIdx)

        val interior = ArrayList<Doc>()
        interior.add(wsBreakAt(children, lparIdx + 1, lparDoc.end, flat = ""))
        var i = lparIdx + 1
        while (i < rparIdx) {
            val entry = children[i]
            if (entry.type == WNodeType.WHITE_SPACE || i == trailingCommaIdx) {
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
        addDynamicTrailingComma(interior, children, trailingCommaIdx)

        val interiorDoc = Doc.Concat(interior, interior.first().start, interior.last().end)
        val closingBreak = wsBreakAt(children, rparIdx - 1, rparDoc.start, flat = "")
        return Doc.Group(Doc.Concat(listOf(lparDoc, Doc.Indent(interiorDoc), closingBreak, rparDoc), start, end))
    }

    private fun Int.isWs(children: List<ChildEntry>): Boolean = children[this].type == WNodeType.WHITE_SPACE

    /**
     * Index of the trailing comma in `children[fromIdx until closeIdx]` — a [WNodeType.COMMA]
     * followed by nothing but whitespace before `closeIdx` — or `null` if there is none.
     */
    private fun trailingCommaIndex(children: List<ChildEntry>, fromIdx: Int, closeIdx: Int): Int? =
        (fromIdx until closeIdx).lastOrNull {
            children[it].type == WNodeType.COMMA && !hasNonWsBetween(children, it + 1, closeIdx)
        }

    /**
     * Appends [Doc.TrailingComma] right after [interior]'s last element when
     * [FormatStyle.trailingCommas] is enabled: its span reuses [trailingCommaIdx]'s original comma
     * when one already sits at the trailing position, or a zero-width point at the last element's
     * end otherwise. [Layout] alone decides whether it renders, from the enclosing [Doc.Group]'s
     * chosen mode — this is the comma-iff-broken mechanism for a fit-driven or threshold-forced
     * list, so a single call site covers both the always-broken and the fits-dependent case.
     */
    private fun addDynamicTrailingComma(interior: MutableList<Doc>, children: List<ChildEntry>, trailingCommaIdx: Int?) {
        if (!style.trailingCommas) return
        val anchor = interior.lastOrNull() ?: return
        val existing = trailingCommaIdx?.let { (children[it] as ChildEntry.Resolved).doc }
        interior.add(Doc.TrailingComma(existing?.start ?: anchor.end, existing?.end ?: anchor.end))
    }

    private fun hasNonWsBetween(children: List<ChildEntry>, from: Int, until: Int): Boolean =
        (from until until).any { children[it].type != WNodeType.WHITE_SPACE }

    /**
     * A [WNodeType.FUN]'s or a [WNodeType.PRIMARY_CONSTRUCTOR]'s own parameter list wraps one
     * parameter per line — a [Doc.Break] between `(`/first parameter, every comma, and the last
     * parameter/`)`, mirroring [resolveArgumentListFrame]'s own break points — either
     * unconditionally (`HARD`, when a non-null [FormatStyle.multilineSignatureThreshold] is met
     * or a parameter's own text already spans multiple lines) or fit-dependently (`SOFT` inside a
     * [Doc.Group], joining back onto one line whenever it fits).
     *
     * Scoped to a [WNodeType.FUN]'s or a [WNodeType.PRIMARY_CONSTRUCTOR]'s own parameter list only
     * ([parentType]): a secondary constructor's, a `FUNCTION_TYPE`'s, or a lambda's parameter list
     * (also `WNodeType.VALUE_PARAMETER_LIST`) falls through to [passthroughParameterList], the
     * pre-existing verbatim/spacing-only handling. A comment directly inside the parameter list
     * also falls through to that path, unwrapped; a comment elsewhere in the signature (return
     * type, modifier list) is not visible from this frame and is not detected.
     */
    private fun resolveValueParameterListFrame(frame: Frame, start: Int, end: Int, parentType: WNodeType?): Doc {
        val children = frame.children
        if (parentType != WNodeType.FUN && parentType != WNodeType.PRIMARY_CONSTRUCTOR) {
            return passthroughParameterList(children, start, end)
        }

        val lparIdx = children.indexOfFirst { it.type == WNodeType.LPAR }
        val rparIdx = children.indexOfLast { it.type == WNodeType.RPAR }
        if (lparIdx < 0 || rparIdx < 0 || rparIdx <= lparIdx) return passthroughParameterList(children, start, end)

        val paramIndices = (lparIdx + 1 until rparIdx).filter { children[it].type == WNodeType.VALUE_PARAMETER }
        if (paramIndices.isEmpty()) return passthroughParameterList(children, start, end)

        val hasComment = (lparIdx + 1 until rparIdx).any { children[it].type in COMMENT_TYPES }
        if (hasComment) return passthroughParameterList(children, start, end)

        val threshold = style.multilineSignatureThreshold
        val forceMultiline = (threshold != null && paramIndices.size >= threshold) ||
            paramIndices.any { spansMultipleLines((children[it] as ChildEntry.Resolved).doc) }
        val breakKind = if (forceMultiline) BreakKind.HARD else BreakKind.SOFT

        val lparDoc = resolveEntry(children[lparIdx])
        val rparDoc = resolveEntry(children[rparIdx])
        val trailingCommaIdx = trailingCommaIndex(children, lparIdx + 1, rparIdx)

        val interior = ArrayList<Doc>()
        interior.add(wsBreakAt(children, lparIdx + 1, lparDoc.end, flat = "", kind = breakKind))
        var i = lparIdx + 1
        while (i < rparIdx) {
            val entry = children[i]
            if (entry.type == WNodeType.WHITE_SPACE || i == trailingCommaIdx) {
                i++
                continue
            }
            val entryDoc = resolveEntry(entry)
            interior.add(entryDoc)
            if (entry.type == WNodeType.COMMA && hasNonWsBetween(children, i + 1, rparIdx)) {
                interior.add(wsBreakAt(children, i + 1, entryDoc.end, flat = " ", kind = breakKind))
            }
            i++
        }
        addDynamicTrailingComma(interior, children, trailingCommaIdx)

        val interiorDoc = Doc.Concat(interior, interior.first().start, interior.last().end)
        val closingBreak = wsBreakAt(children, rparIdx - 1, rparDoc.start, flat = "", kind = breakKind)
        return Doc.Group(Doc.Concat(listOf(lparDoc, Doc.Indent(interiorDoc), closingBreak, rparDoc), start, end))
    }

    /**
     * The pre-existing verbatim/spacing-only handling for a [WNodeType.VALUE_PARAMETER_LIST] not
     * owned by a [WNodeType.FUN]: a constructor's, a [WNodeType.FUNCTION_TYPE]'s, or a lambda's own
     * list first gets the same static trailing-comma decision as
     * [resolveAngleListFrame]/[resolveDestructuringFrame] ([applyTrailingComma], keyed on the
     * list's own already-existing multi-line-ness, since none of these are ever reflowed; a
     * lambda's own list has no [WNodeType.RPAR] of its own, so [closeIdx] falls back to the list's
     * own child count). It then wraps the interior in one [Doc.Indent] and dedents the line holding
     * its own closing `)` when the list already spans multiple lines, mirroring
     * [resolveBraceFrame]'s own placement for a closing `}`; a single-line list is an ordinary
     * [Doc.Concat] with no indent scope of its own.
     */
    private fun passthroughParameterList(children: List<ChildEntry>, start: Int, end: Int): Doc {
        if (children.isEmpty()) return Doc.Concat(emptyList(), start, end)

        val rparIdx = children.indexOfLast { it.type == WNodeType.RPAR }
        val closeIdx = if (rparIdx >= 0) rparIdx else children.size
        val adjusted = applyTrailingComma(children, 0, closeIdx)

        val lastIndex = adjusted.size - 1
        val dedentIndex = lastIndex - 1
        val opensIndentScope = adjusted[lastIndex].type == WNodeType.RPAR &&
            dedentIndex >= 0 && adjusted[dedentIndex] is ChildEntry.Ws
        if (!opensIndentScope) {
            return Doc.Concat(normalizeChildren(adjusted, WNodeType.VALUE_PARAMETER_LIST), start, end)
        }

        val innerParts = normalizeChildren(adjusted.subList(0, dedentIndex), WNodeType.VALUE_PARAMETER_LIST)
        val innerStart = innerParts.firstOrNull()?.start ?: start
        val innerEnd = innerParts.lastOrNull()?.end ?: start
        val parts = mutableListOf<Doc>(Doc.Indent(Doc.Concat(innerParts, innerStart, innerEnd)))
        parts.add(clampWs(adjusted[dedentIndex] as ChildEntry.Ws, newlineCount = 1))
        parts.add(resolveEntry(adjusted[lastIndex]))
        return Doc.Concat(parts, start, end)
    }

    /**
     * A [WNodeType.CLASS]'s own supertype list. Scoped to `parentType == CLASS`
     * ([WNodeType.OBJECT_DECLARATION]'s own supertype list falls through untouched, unwrapped,
     * to [resolveBraceFrame]), and bails the same way on a comment anywhere in the list.
     *
     * One supertype: joins the same line as the constructor's own closing `)` unconditionally
     * when the primary constructor already spans multiple lines ([ctorWrapped], peeked from the
     * still-open [WNodeType.CLASS] frame via [spansMultipleLines] — a fit-undetermined constructor
     * whose own `Group` hasn't yet decided broken-vs-flat reads as not-wrapped here, a narrow,
     * documented approximation); otherwise wrapped in its own [Doc.Group] — `HARD` when the
     * supertype's own text already spans multiple lines, `SOFT` (fit-dependent) otherwise, mirroring
     * [resolveValueParameterListFrame]'s own break-kind choice.
     *
     * Two or more supertypes: always broken, one per line at one [Doc.Indent] level deeper than the
     * class, comma-separated (reusing each source comma's own span) — never fit-dependent. The first
     * supertype joins the constructor's closing line when [ctorWrapped], otherwise the colon ends
     * that line and every supertype, including the first, starts its own line.
     */
    private fun resolveSuperTypeListFrame(frame: Frame, start: Int, end: Int, parentType: WNodeType?): Doc {
        if (parentType != WNodeType.CLASS) return resolveBraceFrame(frame, start, end)
        val children = frame.children
        if (children.any { it.type in COMMENT_TYPES }) return resolveBraceFrame(frame, start, end)
        val entryIndices = children.indices.filter { children[it].type in SUPER_TYPE_ENTRY_TYPES }
        if (entryIndices.isEmpty()) return resolveBraceFrame(frame, start, end)

        frames.lastOrNull()?.ownsSuperTypeListLeadGap = true

        val ctorWrapped = frames.lastOrNull()
            ?.children
            ?.firstOrNull { it.type == WNodeType.PRIMARY_CONSTRUCTOR }
            ?.let { it is ChildEntry.Resolved && spansMultipleLines(it.doc) } == true

        val entryDocs = entryIndices.map { resolveEntry(children[it]) }

        if (entryDocs.size == 1 && ctorWrapped) {
            return Doc.Concat(listOf(Doc.Text(" ", start, start), entryDocs[0]), start, end)
        }
        if (entryDocs.size == 1) {
            val anyMultilineEntry = spansMultipleLines((children[entryIndices[0]] as ChildEntry.Resolved).doc)
            val breakKind = if (anyMultilineEntry) BreakKind.HARD else BreakKind.SOFT
            val lead = Doc.Break(breakKind, flat = " ", start = start, end = start)
            return Doc.Group(Doc.Indent(Doc.Concat(listOf(lead, entryDocs[0]), start, end)))
        }

        val commaIndices = entryIndices.zipWithNext().map { (a, b) ->
            (a + 1 until b).first { children[it].type == WNodeType.COMMA }
        }
        val lead: Doc = if (ctorWrapped) {
            Doc.Text(" ", start, start)
        } else {
            Doc.Break(BreakKind.HARD, literal = "\n", start = start, end = start)
        }

        val body = ArrayList<Doc>()
        body.add(lead)
        body.add(entryDocs[0])
        for (i in 1 until entryDocs.size) {
            val commaIdx = commaIndices[i - 1]
            val commaDoc = resolveEntry(children[commaIdx])
            body.add(commaDoc)
            body.add(wsBreakAt(children, commaIdx + 1, commaDoc.end, flat = " ", kind = BreakKind.HARD))
            body.add(entryDocs[i])
        }
        return Doc.Indent(Doc.Concat(body, start, end))
    }

    /**
     * An annotation container ([WNodeType.MODIFIER_LIST], [WNodeType.ANNOTATED_EXPRESSION]). Bails
     * to [resolveBraceFrame] (verbatim, spacing-only) when: its owning declaration is a
     * [WNodeType.VALUE_PARAMETER] or [WNodeType.VALUE_ARGUMENT] ([parentType] — annotations there
     * always stay inline, regardless of arguments); it has no [WNodeType.ANNOTATION_ENTRY] at all;
     * one is followed directly by a [WNodeType.LAMBDA_EXPRESSION] (an annotated trailing-lambda
     * argument never wraps); a comment sits inside it; or an unrecognized child is present (the
     * `@[...]` array-annotation syntax has no [WNodeType] mapping and resolves to
     * [WNodeType.UNKNOWN]).
     *
     * Otherwise: a single argument-less annotation is left untouched (either placement — same line
     * or its own — is valid, so the source's own choice is preserved); an annotation with arguments
     * ([containsParen], reliable since `(` cannot otherwise appear in an annotation entry's own
     * text) or two or more annotations always wrap, one per line, at the declaration's own depth
     * ([wrapAnnotationEntries]).
     */
    private fun resolveAnnotationContainerFrame(frame: Frame, start: Int, end: Int, parentType: WNodeType?): Doc {
        if (parentType in ANNOTATION_EXEMPT_PARENT_TYPES) return resolveBraceFrame(frame, start, end)
        val children = frame.children
        if (children.any { it.type == WNodeType.UNKNOWN || it.type in COMMENT_TYPES }) return resolveBraceFrame(frame, start, end)
        val entryIndices = children.indices.filter { children[it].type == WNodeType.ANNOTATION_ENTRY }
        if (entryIndices.isEmpty()) return resolveBraceFrame(frame, start, end)
        if (frame.type == WNodeType.ANNOTATED_EXPRESSION && isBeforeLambdaExpression(children, entryIndices.last())) {
            return resolveBraceFrame(frame, start, end)
        }

        val hasArgAnnotation = entryIndices.any { containsParen((children[it] as ChildEntry.Resolved).doc) }
        if (!hasArgAnnotation && entryIndices.size < 2) return resolveBraceFrame(frame, start, end)

        return wrapAnnotationEntries(children, entryIndices, frame.type, start, end)
    }

    private fun isBeforeLambdaExpression(children: List<ChildEntry>, lastEntryIdx: Int): Boolean {
        val next = (lastEntryIdx + 1 until children.size)
            .map { children[it] }
            .firstOrNull { !isPlainWhitespace(it) && it !is ChildEntry.Ws }
        return next?.type == WNodeType.LAMBDA_EXPRESSION
    }

    private fun containsParen(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> doc.value.contains('(')
        is Doc.Concat -> doc.parts.any { containsParen(it) }
        is Doc.Indent -> containsParen(doc.body)
        is Doc.Group -> containsParen(doc.body)
        is Doc.Break -> false
        is Doc.TrailingComma -> false
    }

    /**
     * One annotation entry per line, at the container's own ambient depth (no [Doc.Indent] — the
     * annotations sit at the same depth as the declaration they precede), ending with a forced
     * break before whatever follows ([endsWithHardBreak] lets [adjustAnnotationTrailingGap] drop
     * the now-redundant source gap after this container).
     */
    private fun wrapAnnotationEntries(children: List<ChildEntry>, entryIndices: List<Int>, frameType: WNodeType, start: Int, end: Int): Doc {
        val firstEntry = entryIndices.first()
        val lastEntry = entryIndices.last()
        val parts = ArrayList<Doc>()
        parts.addAll(normalizeChildren(children.subList(0, firstEntry), frameType))
        for ((i, idx) in entryIndices.withIndex()) {
            if (i > 0) {
                val prevIdx = entryIndices[i - 1]
                val prevEnd = (children[prevIdx] as ChildEntry.Resolved).doc.end
                parts.add(wsBreakAt(children, prevIdx + 1, prevEnd, flat = " ", kind = BreakKind.HARD))
            }
            parts.add(resolveEntry(children[idx]))
        }
        val lastEnd = (children[lastEntry] as ChildEntry.Resolved).doc.end
        parts.add(wsBreakAt(children, lastEntry + 1, lastEnd, flat = " ", kind = BreakKind.HARD))
        val gap = children.getOrNull(lastEntry + 1)
        val suffixStart = if (gap != null && (gap is ChildEntry.Ws || isPlainWhitespace(gap))) lastEntry + 2 else lastEntry + 1
        parts.addAll(normalizeChildren(children.subList(suffixStart, children.size), frameType))
        return Doc.Concat(parts, start, end)
    }

    private fun endsWithHardBreak(doc: Doc): Boolean = when (doc) {
        is Doc.Break -> doc.kind == BreakKind.HARD
        is Doc.Concat -> doc.parts.lastOrNull()?.let { endsWithHardBreak(it) } ?: false
        is Doc.Indent -> endsWithHardBreak(doc.body)
        is Doc.Group -> endsWithHardBreak(doc.body)
        is Doc.Text -> false
        is Doc.TrailingComma -> false
    }

    /**
     * Drops the gap right after a resolved [ANNOTATION_CONTAINER_TYPES] entry whose own doc already
     * ends with a forced break ([endsWithHardBreak], set by [wrapAnnotationEntries]) — that break
     * already carries the declaration onto its own line, so the original source gap (real newline
     * or plain space alike) would otherwise double it into a blank line.
     */
    private fun adjustAnnotationTrailingGap(children: List<ChildEntry>): List<ChildEntry> {
        if (children.none { it is ChildEntry.Resolved && it.type in ANNOTATION_CONTAINER_TYPES && endsWithHardBreak(it.doc) }) {
            return children
        }
        val out = ArrayList<ChildEntry>(children.size)
        var i = 0
        while (i < children.size) {
            val entry = children[i]
            out.add(entry)
            if (entry is ChildEntry.Resolved && entry.type in ANNOTATION_CONTAINER_TYPES && endsWithHardBreak(entry.doc)) {
                val gap = children.getOrNull(i + 1)
                i += if (gap != null && (gap is ChildEntry.Ws || isPlainWhitespace(gap))) 2 else 1
            } else {
                i++
            }
        }
        return out
    }

    /**
     * A [WNodeType.TYPE_PARAMETER_LIST]'s or [WNodeType.TYPE_ARGUMENT_LIST]'s own closing
     * [WNodeType.GT] anchors [applyTrailingComma]; everything else about the list (spacing,
     * verbatim line breaks) is unchanged, delegated to [resolveBraceFrame].
     */
    private fun resolveAngleListFrame(frame: Frame, start: Int, end: Int): Doc {
        val closeIdx = frame.children.indexOfFirst { it.type == WNodeType.GT }
        if (closeIdx < 0) return resolveBraceFrame(frame, start, end)
        return resolveBraceFrame(rebuildFrame(frame, applyTrailingComma(frame.children, 0, closeIdx)), start, end)
    }

    /**
     * A [WNodeType.DESTRUCTURING_DECLARATION]'s own closing [WNodeType.RPAR] anchors
     * [applyTrailingComma].
     */
    private fun resolveDestructuringFrame(frame: Frame, start: Int, end: Int): Doc {
        val closeIdx = frame.children.indexOfLast { it.type == WNodeType.RPAR }
        if (closeIdx < 0) return resolveBraceFrame(frame, start, end)
        return resolveBraceFrame(rebuildFrame(frame, applyTrailingComma(frame.children, 0, closeIdx)), start, end)
    }

    /**
     * A [WNodeType.WHEN_ENTRY]'s own [WNodeType.ARROW] anchors [applyTrailingComma] over its
     * condition list, bailing entirely for: an `else` entry; an entry whose enclosing `when` has
     * no parenthesized subject ([hasSubject]) — a subject-less entry's grammar has no comma
     * production at all, so inserting one would break compilation; or an entry containing a
     * structurally-unrecognized child ([WNodeType.UNKNOWN] — a guard clause has no [WNodeType] of
     * its own, so this is the only way to detect one). In all three cases the entry is left
     * untouched.
     */
    private fun resolveWhenEntryFrame(frame: Frame, start: Int, end: Int): Doc {
        val children = frame.children
        val arrowIdx = children.indexOfFirst { it.type == WNodeType.ARROW }
        val hasSubject = frames.lastOrNull()?.children?.any { it.type == WNodeType.LPAR } == true
        val bail = arrowIdx < 0 || !hasSubject ||
            (0 until arrowIdx).any { children[it].type == WNodeType.KW_ELSE || children[it].type == WNodeType.UNKNOWN }
        val adjusted = if (bail) children else applyTrailingComma(children, 0, arrowIdx)
        return resolveBraceFrame(rebuildFrame(frame, adjusted), start, end)
    }

    private fun rebuildFrame(frame: Frame, children: List<ChildEntry>): Frame =
        Frame(frame.type).also { it.children.addAll(children) }

    /**
     * The static trailing-comma decision for a list [resolveBraceFrame] renders verbatim (never
     * reflowed): present when [FormatStyle.trailingCommas] is enabled and any child in
     * `children[fromIdx until closeIdx]` already spans multiple lines ([isMultilineEntry]), absent
     * otherwise — inserted or removed once, here, at build time (unlike
     * [addDynamicTrailingComma]'s per-render decision for a list the printer actually reflows).
     * `closeIdx` need not be a real delimiter's own index — a lambda's own parameter list has none
     * of its own, so callers pass `children.size` to mean "right after the last child".
     */
    private fun applyTrailingComma(children: List<ChildEntry>, fromIdx: Int, closeIdx: Int): List<ChildEntry> {
        val hasContent = (fromIdx until closeIdx).any {
            children[it].type != WNodeType.COMMA && !isPlainWhitespace(children[it]) && children[it] !is ChildEntry.Ws
        }
        if (!hasContent) return children

        val existingIdx = trailingCommaIndex(children, fromIdx, closeIdx)
        if (!style.trailingCommas) {
            return if (existingIdx != null) removeTrailingComma(children, existingIdx) else children
        }
        val multiline = (fromIdx until closeIdx).any { isMultilineEntry(children[it]) }
        return when {
            multiline && existingIdx == null -> insertTrailingComma(children, closeIdx)
            !multiline && existingIdx != null -> removeTrailingComma(children, existingIdx)
            else -> children
        }
    }

    private fun isMultilineEntry(entry: ChildEntry): Boolean = when (entry) {
        is ChildEntry.Ws -> true
        is ChildEntry.Resolved -> spansMultipleLines(entry.doc)
    }

    private fun insertTrailingComma(children: List<ChildEntry>, closeIdx: Int): List<ChildEntry> {
        val lastContentIdx = (0 until closeIdx).lastOrNull {
            children[it].type != WNodeType.COMMA && !isPlainWhitespace(children[it]) && children[it] !is ChildEntry.Ws
        } ?: return children
        val anchor = (children[lastContentIdx] as ChildEntry.Resolved).doc.end
        val comma = ChildEntry.Resolved(WNodeType.COMMA, Doc.Text(",", anchor, anchor))
        return children.toMutableList().also { it.add(lastContentIdx + 1, comma) }
    }

    private fun removeTrailingComma(children: List<ChildEntry>, existingIdx: Int): List<ChildEntry> =
        children.toMutableList().also { it.removeAt(existingIdx) }

    private fun spansMultipleLines(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> doc.value.contains('\n')
        is Doc.Break -> doc.kind == BreakKind.HARD
        is Doc.TrailingComma -> false
        is Doc.Indent -> spansMultipleLines(doc.body)
        is Doc.Group -> spansMultipleLines(doc.body)
        is Doc.Concat -> doc.parts.any { spansMultipleLines(it) }
    }

    /**
     * Builds [children] with one `SOFT` [Doc.Break] spliced in at [anchorIndex] ([breakBefore] it
     * or after it), consuming the adjacent whitespace child in its place if one is there. Shared by
     * [resolveChainFrame] (break before the dot/safe-access operator) and [resolveBinaryFrame]
     * (break after the operator, or before it for `?:`).
     *
     * The gap on the other side of [anchorIndex] wants the same [flat] text but is never itself a
     * break candidate: a plain, single-line `WHITE_SPACE` there is normalized to [flat] directly; a
     * real newline on that side is left untouched.
     */
    private fun spliceBreak(children: List<ChildEntry>, anchorIndex: Int, breakBefore: Boolean, flat: String): List<Doc> {
        val wsIndex = if (breakBefore) anchorIndex - 1 else anchorIndex + 1
        val hasWs = wsIndex in children.indices && wsIndex.isWs(children)
        val insertIndex = if (breakBefore) anchorIndex else anchorIndex + 1
        val anchorDoc = (children[anchorIndex] as ChildEntry.Resolved).doc
        val fallback = if (breakBefore) anchorDoc.start else anchorDoc.end
        val breakDoc = wsBreakAt(children, wsIndex, fallback, flat)

        val otherWsIndex = if (breakBefore) anchorIndex + 1 else anchorIndex - 1
        val otherIsPlainWs = otherWsIndex in children.indices && isPlainWhitespace(children[otherWsIndex])

        val parts = ArrayList<Doc>(children.size + 1)
        for (idx in children.indices) {
            if (idx == insertIndex) parts.add(breakDoc)
            if (hasWs && idx == wsIndex) continue
            if (otherIsPlainWs && idx == otherWsIndex) {
                val ws = (children[idx] as ChildEntry.Resolved).doc
                parts.add(Doc.Text(flat, ws.start, ws.end))
                continue
            }
            parts.add(resolveEntry(children[idx]))
        }
        if (insertIndex == children.size) parts.add(breakDoc)
        return parts
    }

    /**
     * A [kind] [Doc.Break] (`SOFT` by default) at [wsIndex]: if [children] has a `WHITE_SPACE`-typed
     * child there, the break's span claims exactly the whitespace it replaces; otherwise a
     * zero-width break is anchored at [fallback]. [flat] always wins over whatever whitespace it
     * replaces.
     */
    private fun wsBreakAt(children: List<ChildEntry>, wsIndex: Int, fallback: Int, flat: String, kind: BreakKind = BreakKind.SOFT): Doc.Break {
        val entry = children.getOrNull(wsIndex)
        return if (entry != null && entry.type == WNodeType.WHITE_SPACE) {
            val (spanStart, spanEnd) = when (entry) {
                is ChildEntry.Ws -> entry.start to entry.start + entry.rawText.length
                is ChildEntry.Resolved -> entry.doc.start to entry.doc.end
            }
            Doc.Break(kind, flat = flat, start = spanStart, end = spanEnd)
        } else {
            Doc.Break(kind, flat = flat, start = fallback, end = fallback)
        }
    }

    private fun flatText(doc: Doc): String = when (doc) {
        is Doc.Text -> doc.value
        is Doc.Concat -> doc.parts.joinToString("") { flatText(it) }
        else -> ""
    }

    /**
     * The context-free fallback for every [ChildEntry.Ws] resolved without going through
     * [normalizeChildren]'s [verticalGapNewlineCount] dispatch: caps at most one blank line,
     * unconditionally.
     */
    private fun resolveEntry(entry: ChildEntry): Doc = when (entry) {
        is ChildEntry.Resolved -> entry.doc
        is ChildEntry.Ws -> {
            val actual = entry.rawText.count { it == '\n' }
            clampWs(entry, if (actual > 2) 2 else actual)
        }
    }

    private class Frame(val type: WNodeType) {
        val children = mutableListOf<ChildEntry>()
        var ownsSuperTypeListLeadGap = false
    }

    private sealed interface ChildEntry {
        val type: WNodeType

        class Resolved(override val type: WNodeType, val doc: Doc) : ChildEntry
        class Ws(val rawText: String, val start: Int) : ChildEntry {
            override val type: WNodeType = WNodeType.WHITE_SPACE
        }
    }
}
