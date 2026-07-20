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

        WNodeType.PREFIX_EXPRESSION, WNodeType.POSTFIX_EXPRESSION -> resolveUnaryFrame(frame, start, end)

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
        val children = if (frame.type == WNodeType.FUNCTION_LITERAL) normalizeLambdaBraces(frame.children) else frame.children
        if (children.isEmpty()) return Doc.Concat(emptyList(), start, end)

        val lastIndex = children.size - 1
        val opensIndentScope = frame.type in INDENTING_TYPES && children[lastIndex].type == WNodeType.RBRACE
        if (!opensIndentScope) {
            return Doc.Concat(normalizeChildren(children, frame.type, ancestorHasFun(frame.type)), start, end)
        }

        val dedentIndex = lastIndex - 1
        val hasDedent = dedentIndex >= 0 && children[dedentIndex] is ChildEntry.Ws

        val innerCount = if (hasDedent) dedentIndex else lastIndex
        val innerParts = normalizeChildren(children.subList(0, innerCount), frame.type, ancestorHasFun(frame.type))

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
     * `no-empty-first-line-in-method-block`'s own ground truth (`isPartOf(FUN)`, an unbounded
     * ancestor walk, not "direct parent") requires knowing whether *any* enclosing frame is a
     * [WNodeType.FUN] — available here because [frames] still holds every still-open ancestor at
     * the moment a child frame is resolved (the child's own frame was already popped).
     */
    private fun ancestorHasFun(frameType: WNodeType): Boolean =
        frameType == WNodeType.BLOCK && frames.any { it.type == WNodeType.FUN }

    /**
     * A [WNodeType.PREFIX_EXPRESSION]/[WNodeType.POSTFIX_EXPRESSION] (`-x`, `!x`, `x++`, `x!!`) is
     * tight to its operand always, unconditionally — the frame-type dispatch in [resolveFrame] is
     * itself the unary-vs-binary disambiguation (the same structural signal ktlint's
     * `SpacingAroundUnaryOperatorRule` uses), so no token-level guessing is needed here: every
     * single-line whitespace child inside one of these two frames collapses to nothing.
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
     * `{`/`}` spacing for a lambda body (`{ x -> ... }`), the one curly-brace concern this slice
     * covers (ordinary `BLOCK`/`CLASS_BODY`/`WHEN` braces are always followed by a real line break
     * in practice and need no horizontal decision). Only the gap right after the opening `{` and
     * right before the closing `}` are in scope — a genuine newline there (multi-line body) is left
     * untouched, since this slice normalizes horizontal space only. A lambda with nothing between
     * its braces but whitespace collapses to `{}`; anything else gets exactly one space on that side.
     */
    private fun normalizeLambdaBraces(children: List<ChildEntry>): List<ChildEntry> {
        if (children.size < 2) return children
        return normalizeLambdaTail(normalizeLambdaHead(children))
    }

    /**
     * A lambda's body is always a [WNodeType.BLOCK] child, even when nothing was written between
     * `{`/`}` and `->` — a real, empty `BLOCK` that renders as zero characters (confirmed off a real
     * LightTree dump: `names.forEach {}` is `[LBRACE, BLOCK(empty), RBRACE]`, never bare
     * `[LBRACE, RBRACE]`). Treating only a literal `RBRACE`/`LBRACE` neighbor as "nothing here" is
     * therefore not enough — an empty `BLOCK` must count the same way, or the empty-lambda case
     * gets a space inserted on both sides of it instead of staying `{}`. Emptiness is decided from
     * the resolved `Doc`'s actual rendered content (recursively: an empty `Text`, or a `Concat` whose
     * parts are all empty), never from its source span — [Doc.start]/[Doc.end] both default to `0`
     * for a `Doc` built without real offsets (every hand-built [DocBuilderSpec] fixture), which would
     * make a span-equality check misfire as "empty" for perfectly real content.
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
     * doesn't already give a dedicated `Group`/break treatment to (chains, binary expressions and
     * call argument lists keep doing their own thing via [wsBreakAt]'s `flat` parameter, which
     * already normalizes their anchor gaps). For every gap between two direct children — whether
     * represented by an actual single-line [WNodeType.WHITE_SPACE] child or by no child at all (two
     * tokens directly adjacent) — [spacingDecision] is asked for the correct rendering; `null` means
     * "no rule for this pair, preserve whatever was there verbatim" (§the printer contract's
     * "preserve, don't guess" for anything this slice doesn't cover). A real newline
     * ([ChildEntry.Ws]) is never touched for horizontal spacing here — its own newline *count* is
     * instead normalized by [verticalGapNewlineCount]/[clampWs] (Phase C.6's blank-line policy),
     * the vertical analog of the same choke point.
     */
    private fun normalizeChildren(children: List<ChildEntry>, frameType: WNodeType, ancestorHasFun: Boolean = false): List<Doc> {
        val out = ArrayList<Doc>(children.size + 2)
        for (i in children.indices) {
            val entry = children[i]
            if (entry is ChildEntry.Ws) {
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
                if (spacingDecision(frameType, entry.type, next.type) == " ") {
                    val pos = out.last().end
                    out.add(Doc.Text(" ", pos, pos))
                }
            }
        }
        return out
    }

    /**
     * Phase C.6's blank-line policy table, ground-truthed against ktlint's own rules and tests:
     * `no-consecutive-blank-lines` (default: at most one blank line anywhere, i.e. clamp an
     * over-long gap down to 2 newlines, never up), `no-blank-line-before-rbrace` (handled directly
     * at [resolveBraceFrame]'s own dedent call site, not here — RBRACE never appears as a `next`
     * sibling reaching this function), `no-empty-first-line-in-method-block`/
     * `-in-class-body` (the gap right after `{` collapses to zero blank lines, scoped to
     * [WNodeType.CLASS_BODY] unconditionally or [WNodeType.BLOCK] only when [ancestorHasFun] —
     * mirroring `isPartOf(FUN)`'s unbounded-ancestor check — never [WNodeType.WHEN]/
     * [WNodeType.FUNCTION_LITERAL], which ktlint's rules don't cover either), the class-name/
     * primary-constructor gap (`no-consecutive-blank-lines`' own special case: zero blank lines,
     * never one), and `package-import-spacing`/`spacing-after-package-and-imports` (exactly one
     * blank line — the only case that can *add* a newline, not just cap one — between a non-empty
     * package directive and a non-empty import list, and between that import list and whatever
     * follows it, gated on [entryHasContent] so an absent package statement or an empty import list
     * never forces a blank line into existence).
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
     * The normalization table for the ~13 pure-spacing concerns this slice covers (design.md
     * Phase C.5), ground-truthed against ktlint's own rule implementations and tests rather than
     * assumed style: no space before a comma, one space after (none before a closing delimiter);
     * colon spacing keyed on the enclosing declaration ([COLON_WANTS_SPACE_BOTH_SIDES] — supertype
     * list, secondary-constructor delegation, generic bound — versus the default type-annotation
     * colon with no space before and one after, and no space at all for an annotation's use-site
     * target colon); one space after `if`/`when`/`for`/`while`/`catch`
     * ([KEYWORDS_WANTING_SPACE_AFTER]) regardless of what follows; a spread operator's `*` tight to
     * its argument; no space just inside `(`/`)`/`[`/`]`, none between a name and its parameter or
     * argument list (declaration *and* call site — one rule, matching ktlint's own division of
     * labor — except a `FUNCTION_TYPE`'s own parameter list, which may legitimately carry a
     * preceding annotation, and a `FUNCTION_LITERAL`'s own parameter list, whose gap from `{` is
     * [normalizeLambdaHead]'s concern, not this one's); no space just inside `<`/`>` when the
     * enclosing frame is itself a [WNodeType.TYPE_PARAMETER_LIST]/[WNodeType.TYPE_ARGUMENT_LIST]
     * (the same structural signal that keeps a comparison `<`/`>` — a
     * [WNodeType.BINARY_EXPRESSION] frame — untouched here);
     * `::` tight after always (the "bound reference with no receiver" gap *before* `::` needs
     * context this walk doesn't have — see the disambiguation note in the class KDoc — so that side
     * is left alone, `null`); `..`/`..<` tight both sides; no space before `?`.
     *
     * `null` means "preserve verbatim" — deliberately, per the printer contract, for every pair this
     * table doesn't recognize (an unrequested nuance, or one this walk can't cheaply disambiguate).
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
     * from; only elvis moves to the start of the next line, alongside `.`/`?.`). The flat-form gap
     * is one space for every operator but the range operator (`..`, itself a `BINARY_EXPRESSION` in
     * Kotlin's own grammar, not a separate construct) — Phase C.5's ground truth
     * (`SpacingAroundRangeOperatorRule`: tight, unconditionally) means this generic flat=" " default
     * would otherwise have silently *spaced* `1..5`, a C.4 assumption this slice's fixtures caught
     * on contact.
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
     *
     * The gap on the *other* side of [anchorIndex] wants the same [flat] text but is never itself a
     * line-break candidate (`a  +  b`'s two operator gaps are symmetric spacing, not two independent
     * decisions) — a plain, single-line `WHITE_SPACE` there is normalized to [flat] directly as a
     * [Doc.Text], never left to [resolveEntry]'s verbatim default; a real newline on that side is
     * left untouched (this slice normalizes horizontal space only).
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

    /**
     * The default, context-free half of Phase C.6's blank-line policy (`no-consecutive-blank-lines`:
     * at most one blank line, everywhere — ktlint's own rule applies to any whitespace token
     * regardless of its parent) — the fallback for every [ChildEntry.Ws] this class resolves
     * *without* going through [normalizeChildren]'s more specific, context-aware
     * [verticalGapNewlineCount] dispatch (a chain/binary expression's non-anchor whitespace, a
     * unary frame's interior, an argument list's fallback path with no real `(`/`)` found).
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
    }

    private sealed interface ChildEntry {
        val type: WNodeType

        class Resolved(override val type: WNodeType, val doc: Doc) : ChildEntry
        class Ws(val rawText: String, val start: Int) : ChildEntry {
            override val type: WNodeType = WNodeType.WHITE_SPACE
        }
    }
}
