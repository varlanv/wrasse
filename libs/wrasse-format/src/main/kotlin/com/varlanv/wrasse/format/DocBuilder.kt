package com.varlanv.wrasse.format

import com.varlanv.wrasse.lang.NoopPerf
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPerf
import com.varlanv.wrasse.lang.containsChar
import com.varlanv.wrasse.lang.indexOfChar
import com.varlanv.wrasse.lang.lastIndexOfChar
import com.varlanv.wrasse.model.EditPlan
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WNodeTypeSet
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val INDENTING_TYPES = WNodeTypeSet.containing(
    WNodeType.BLOCK,
    WNodeType.CLASS_BODY,
    WNodeType.WHEN,
    WNodeType.FUNCTION_LITERAL,
)
private val CHAIN_LINK_TYPES = WNodeTypeSet.containing(
    WNodeType.DOT_QUALIFIED_EXPRESSION,
    WNodeType.SAFE_ACCESS_EXPRESSION,
)
private val BINARY_SPREAD_TYPES = WNodeTypeSet.containing(WNodeType.BINARY_EXPRESSION)
private val COMMENT_TYPES = WNodeTypeSet.containing(WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT)
private val SUPER_TYPE_SEPARATOR_TYPES = WNodeTypeSet.containing(WNodeType.COMMA, WNodeType.WHITE_SPACE)
private val LIST_BRACKET_TYPES = WNodeTypeSet.containing(
    WNodeType.LPAR,
    WNodeType.RPAR,
    WNodeType.LT,
    WNodeType.GT,
    WNodeType.LBRACKET,
    WNodeType.RBRACKET,
)
private val ANNOTATION_CONTAINER_TYPES = WNodeTypeSet.containing(
    WNodeType.MODIFIER_LIST,
    WNodeType.ANNOTATED_EXPRESSION,
)
private val ANNOTATION_EXEMPT_PARENT_TYPES = WNodeTypeSet.containing(
    WNodeType.VALUE_PARAMETER,
    WNodeType.VALUE_ARGUMENT,
)
private val ASSIGNMENT_OPERATOR_TEXTS = setOf("=", "+=", "-=", "*=", "/=", "%=")

private const val CHAIN_LINK_BREAK_THRESHOLD = 2

private val CONDITION_PAREN_TYPES = WNodeTypeSet.containing(
    WNodeType.IF,
    WNodeType.WHILE,
    WNodeType.DO_WHILE,
)

private val OWN_LINE_FORCE_TYPES = WNodeTypeSet.containing(WNodeType.BLOCK, WNodeType.CLASS_BODY, WNodeType.WHEN)
private val SEMICOLON_BREAK_SCOPE = WNodeTypeSet.containing(WNodeType.BLOCK, WNodeType.WHEN)
private val HUGGING_VALUE_TYPES = WNodeTypeSet.containing(WNodeType.IF, WNodeType.WHEN, WNodeType.TRY)
private val BLANK_LINE_BEFORE_DECLARATION_TYPES = WNodeTypeSet.containing(
    WNodeType.CLASS,
    WNodeType.CLASS_INITIALIZER,
    WNodeType.FUN,
    WNodeType.OBJECT_DECLARATION,
    WNodeType.PROPERTY,
)
private val DECLARATION_SPACING_TYPES = WNodeTypeSet.containing(
    WNodeType.CLASS,
    WNodeType.CLASS_INITIALIZER,
    WNodeType.FUN,
    WNodeType.OBJECT_DECLARATION,
    WNodeType.PROPERTY,
    WNodeType.TYPEALIAS,
    WNodeType.SECONDARY_CONSTRUCTOR,
    WNodeType.ENUM_ENTRY,
)
private val DECLARATION_GAP_CONTAINER_TYPES = WNodeTypeSet.containing(
    WNodeType.FILE,
    WNodeType.CLASS_BODY,
    WNodeType.BLOCK,
)
private val ANY_COMMENT_TYPES = WNodeTypeSet.containing(
    WNodeType.EOL_COMMENT,
    WNodeType.BLOCK_COMMENT,
    WNodeType.KDOC,
)
private val BLOCK_COMMENT_TYPES = WNodeTypeSet.containing(WNodeType.BLOCK_COMMENT, WNodeType.KDOC)
private val EOL_COMMENT_EXEMPT_PREFIXES = listOf("//noinspection", "//region", "//endregion", "//language=")

private val KEYWORDS_WANTING_SPACE_AFTER = WNodeTypeSet.containing(
    WNodeType.KW_IF,
    WNodeType.KW_WHEN,
    WNodeType.KW_FOR,
    WNodeType.KW_WHILE,
    WNodeType.KW_CATCH,
    WNodeType.KW_WHERE,
)
private val CLOSERS_NOT_NEEDING_SPACE_AFTER_COMMA = WNodeTypeSet.containing(
    WNodeType.RPAR,
    WNodeType.RBRACKET,
    WNodeType.GT,
    WNodeType.RBRACE,
)
private val COLON_WANTS_SPACE_BOTH_SIDES = WNodeTypeSet.containing(
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
 * ambient [Doc.Indent] depth. A multi-line [WNodeType.KDOC]/[WNodeType.BLOCK_COMMENT] is split the
 * same way, one `HARD` break per line ([reindentableComment]), so its continuation lines follow the
 * comment's new indent. [resolveChainFrame]/[resolveBinaryFrame]/[resolveArgumentListFrame]/
 * [resolveValueParameterListFrame] replace the adjacent whitespace with a `SOFT` break inside a
 * [Doc.Group] instead, so the printer decides whether that line joins or stays split.
 *
 * A node's direct children are buffered until [exitNode], since a node's own layout can only be
 * decided once its children are complete — see [resolveFrame] and its per-construct helpers.
 */
class DocBuilder(formatConfig: WFormatConfig) : WStreamRule {
    override val id: String = "format"
    override val config: WrasseRuleConfig = formatConfig.ruleConfig

    private val style = formatConfig.style
    private val frames = ArrayDeque<Frame>()
    private var editPlan: EditPlan? = null
    private var templateEntryDepth = 0
    private var rootDoc: Doc = Doc.Concat(emptyList())

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        val text: CharSequence = ctx.leafText ?: ""
        val entry = when (ctx.type) {
            WNodeType.WHITE_SPACE if text.containsChar('\n') -> ChildEntry.Ws(text, ctx.startOffset)
            WNodeType.EOL_COMMENT -> ChildEntry.Resolved(
                ctx.type,
                Doc.Text(normalizeEolCommentText(text), ctx.startOffset, ctx.endOffset),
            )

            WNodeType.KDOC, WNodeType.BLOCK_COMMENT -> ChildEntry.Resolved(
                ctx.type,
                if (text.containsChar('\n')) {
                    reindentableComment(text, ctx.startOffset, lineIndentOf(ctx.sourceText, ctx.startOffset))
                } else {
                    Doc.Text(text, ctx.startOffset, ctx.endOffset)
                },
            )

            else -> ChildEntry.Resolved(ctx.type, Doc.Text(text, ctx.startOffset, ctx.endOffset))
        }
        frames.last().children.add(entry)
    }

    /**
     * An [WNodeType.EOL_COMMENT]'s own text, unchanged unless it is bare (`//`), already starts
     * with `// `, or starts with `//noinspection`/`//region`/`//endregion`/`//language=` — otherwise
     * a space is inserted right after `//`. Block comments and KDoc are never touched.
     */
    private fun normalizeEolCommentText(text: CharSequence): CharSequence = when {
        text.length == 2 -> text
        text.startsWith("// ") -> text
        EOL_COMMENT_EXEMPT_PREFIXES.any { text.startsWith(it) } -> text
        else -> "// " + text.removePrefix("//")
    }

    /** Width of the leading whitespace on the line [offset] sits on in [source]. */
    private fun lineIndentOf(source: CharSequence, offset: Int): Int {
        var lineStart = offset - 1
        while (lineStart >= 0 && source[lineStart] != '\n') lineStart--
        lineStart++
        var i = lineStart
        while (i < offset && (source[i] == ' ' || source[i] == '\t')) i++
        return i - lineStart
    }

    /**
     * A multi-line [WNodeType.KDOC]/[WNodeType.BLOCK_COMMENT] as its opener [Doc.Text] followed by
     * one `HARD` [Doc.Break] plus one [Doc.Text] per continuation line, so [Layout] re-derives each
     * continuation's leading whitespace from the ambient [Doc.Indent] depth instead of keeping the
     * column the source happened to use. Only that leading whitespace changes: a line whose content
     * starts with `*` gets exactly one space before it, any other line keeps its original indent
     * relative to [openerLineIndent], the indentation of the line the opener sits on (never
     * negative — so re-indenting the same comment again is a no-op), and a line with no content at
     * all is folded into the preceding break's literal so it stays truly empty.
     */
    private fun reindentableComment(
        text: CharSequence,
        start: Int,
        openerLineIndent: Int,
    ): Doc {
        val firstNewline = text.indexOfChar('\n')
        val parts = ArrayList<Doc>()
        parts.add(Doc.Text(text.subSequence(0, firstNewline), start, start + firstNewline))
        var breakStart = firstNewline
        var newlines = 0
        var cursor = firstNewline
        while (cursor < text.length) {
            newlines++
            val lineStart = cursor + 1
            var contentStart = lineStart
            while (contentStart < text.length && (text[contentStart] == ' ' || text[contentStart] == '\t')) {
                contentStart++
            }
            val newlineIdx = text.indexOfChar('\n', contentStart)
            val lineEnd = if (newlineIdx < 0) text.length else newlineIdx
            cursor = lineEnd
            if (contentStart == lineEnd) continue
            parts.add(
                Doc.Break(
                    BreakKind.HARD,
                    literal = "\n".repeat(newlines),
                    start = start + breakStart,
                    end = start + contentStart,
                ),
            )
            val lead = if (text[contentStart] == '*') {
                " "
            } else {
                " ".repeat(maxOf(0, contentStart - lineStart - openerLineIndent))
            }
            parts.add(Doc.Text(lead + text.subSequence(contentStart, lineEnd), start + contentStart, start + lineEnd))
            newlines = 0
            breakStart = lineEnd
        }
        return Doc.Concat(parts, start, start + text.length)
    }

    override fun enterNode(ctx: WContext) {
        if (ctx.type == WNodeType.LONG_STRING_TEMPLATE_ENTRY) templateEntryDepth++
        frames.addLast(Frame(ctx.type))
    }

    override fun exitNode(ctx: WContext) {
        editPlan = ctx.editPlan
        val frame = frames.removeLast()
        if (frame.type == WNodeType.LONG_STRING_TEMPLATE_ENTRY) templateEntryDepth--
        if (frame.type == WNodeType.BLOCK) frame.branchOfMultilineIf = isBranchOfMultilineIf(ctx)
        val parentType = frames.lastOrNull()?.type
        val doc = resolveFrame(frame, ctx.startOffset, ctx.endOffset, parentType)
        if (frames.isEmpty()) {
            rootDoc = doc
        } else {
            val firstChildType = frame.children.firstOrNull()?.type
            val hasLeadingComment = firstChildType != null && firstChildType in ANY_COMMENT_TYPES
            val carriesComment = frame.children.any { carriesComment(it) }
            frames
                .last()
                .children
                .add(
                    ChildEntry.Resolved(
                        ctx.type,
                        doc,
                        frame.hasLeadingAnnotation,
                        hasLeadingComment,
                        frame.reindentedRawString,
                        frame.isQualifiedNameChain,
                        frame.hugsLambdaArgument,
                        frame.wrapsCallLike,
                        frame.chainHeadIsRawString,
                        frame.hasArguments,
                        frame.isCallWithArguments,
                        frame.endsWithCallWithArguments,
                        frame.hasChainComment,
                        frame.chainCallLinks,
                        carriesComment,
                    ),
                )
        }
    }

    /**
     * Splices every edit still in [WContext.editPlan] into [rootDoc], then renders. Must be called
     * exactly once, after the walk's `afterFile` phase and its deferred
     * [com.varlanv.wrasse.model.WFileRule] phase have both completed, so every rule's final edit is
     * already in the plan. Overlapping edits are resolved via [EditPlan.resolveOverlaps] first —
     * [DocSplicer] still assumes disjoint input — so a dropped edit's finding is reported but not
     * spliced here either; it survives into the next pass same as it would outside format mode. Once
     * splicing is committed to, the dropped entries are handed to [EditPlan.recordDropped] since they
     * never return to the plan for a later [EditPlan.finalEdits] call to see them itself.
     * [DocSplicer.splice] returning `null` means at least one surviving edit could not be cleanly
     * mapped onto a `Doc` leaf: the format pass is skipped for this compile (no report, no edit)
     * and the plan is handed back untouched (every taken entry, not just the kept ones) so the
     * declining rules' own edits still reach the patch.
     */
    fun finish(
        ctx: WContext,
        reporter: WReporter,
        perf: WPerf = NoopPerf,
    ) {
        val original = ctx.sourceText.toString()
        val taken = ctx.editPlan.takeAll()
        val (kept, dropped) = EditPlan.resolveOverlaps(taken)
        val spliceStarted = if (perf.enabled) System.nanoTime() else 0L
        val spliced = DocSplicer.splice(rootDoc, kept.map { it.edit })
        if (perf.enabled) perf.record("phase:format-splice", System.nanoTime() - spliceStarted)
        if (spliced == null) {
            ctx.editPlan.restore(taken)
            if (perf.enabled) perf.add("count:format-splice-bailed", 1)
            return
        }
        ctx.editPlan.recordDropped(dropped)
        val renderStarted = if (perf.enabled) System.nanoTime() else 0L
        val rendered = Layout.render(spliced, style)
        if (perf.enabled) perf.record("phase:format-render", System.nanoTime() - renderStarted)
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
    private fun resolveFrame(
        frame: Frame,
        start: Int,
        end: Int,
        parentType: WNodeType?,
    ): Doc = when (frame.type) {
        WNodeType.DOT_QUALIFIED_EXPRESSION, WNodeType.SAFE_ACCESS_EXPRESSION -> resolveChainFrame(
            frame,
            start,
            end,
            isRoot = parentType == null || parentType !in CHAIN_LINK_TYPES,
        )

        WNodeType.BINARY_EXPRESSION -> resolveBinaryFrame(
            frame,
            start,
            end,
            isRoot = parentType != WNodeType.BINARY_EXPRESSION,
        )

        WNodeType.VALUE_ARGUMENT_LIST -> resolveArgumentListFrame(frame, start, end)

        WNodeType.VALUE_ARGUMENT -> resolveValueArgumentFrame(frame, start, end)

        WNodeType.CALL_EXPRESSION -> resolveCallExpressionFrame(frame, start, end)

        WNodeType.LONG_STRING_TEMPLATE_ENTRY -> Doc.Group(resolveBraceFrame(frame, start, end), GroupKind.TEMPLATE)

        WNodeType.IF, WNodeType.WHEN, WNodeType.TRY, WNodeType.OBJECT_LITERAL -> Doc.Group(
            resolveBraceFrame(frame, start, end),
            GroupKind.BARRIER,
        )

        WNodeType.SUPER_TYPE_CALL_ENTRY -> resolveSuperTypeCallEntryFrame(frame, start, end)

        WNodeType.VALUE_PARAMETER_LIST -> resolveValueParameterListFrame(frame, start, end, parentType)

        WNodeType.TYPE_PARAMETER_LIST, WNodeType.TYPE_ARGUMENT_LIST -> resolveAngleListFrame(frame, start, end)

        WNodeType.DESTRUCTURING_DECLARATION -> resolveDestructuringFrame(frame, start, end)

        WNodeType.WHEN_ENTRY -> resolveWhenEntryFrame(frame, start, end)

        WNodeType.SUPER_TYPE_LIST -> resolveSuperTypeListFrame(frame, start, end, parentType)

        WNodeType.MODIFIER_LIST, WNodeType.ANNOTATED_EXPRESSION -> resolveAnnotationContainerFrame(
            frame,
            start,
            end,
            parentType,
        )

        WNodeType.PROPERTY -> resolvePropertyFrame(frame, start, end)

        WNodeType.FUN -> resolveFunFrame(frame, start, end)

        WNodeType.TYPEALIAS -> resolveTypealiasFrame(frame, start, end)

        WNodeType.PREFIX_EXPRESSION, WNodeType.POSTFIX_EXPRESSION -> resolveUnaryFrame(frame, start, end)

        WNodeType.STRING_TEMPLATE -> resolveStringTemplateFrame(frame, start, end)

        WNodeType.FUNCTION_LITERAL -> resolveFunctionLiteralFrame(frame, start, end)

        else -> resolveBraceFrame(frame, start, end)
    }

    /**
     * A node in [INDENTING_TYPES] wraps its interior in one [Doc.Indent] and dedents the line
     * holding its own closing `RBRACE`, but only when its own last child is literally `RBRACE` — a
     * lambda body's `BLOCK` has no `{`/`}` of its own (those belong to the enclosing
     * `FUNCTION_LITERAL`) and is a transparent pass-through instead.
     */
    private fun resolveBraceFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val rawChildren =
            if (frame.type == WNodeType.FUNCTION_LITERAL) normalizeLambdaBraces(frame.children) else frame.children
        val conditionFolded = foldConditionParens(rawChildren, frame.type)
        val annotationAdjusted = adjustAnnotationTrailingGap(conditionFolded)
        val semicolonAdjusted = convertStatementSeparatorSemicolons(annotationAdjusted, frame.type)
        val children = forceMultilineBraceGaps(semicolonAdjusted, frame.type, frame.branchOfMultilineIf)
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
     * Collapses `(`, an `if`/`while` condition and `)` into one [Doc.Group] whenever the condition
     * was written across lines: the condition itself starts on the construct's own line and a
     * `SOFT` break before `)` decides the rest — the whole condition joins that line when it fits
     * there together with what follows `)`, and otherwise wraps at its own operators one indent
     * level in, leaving `)` alone on a line at the construct's own indent. Every multi-line
     * condition therefore renders the same way, whichever side of it the author's own newline was
     * written on.
     *
     * A no-op for a condition written entirely on one line, for a construct with no
     * [WNodeType.CONDITION] child of its own (a `for` loop, a `when` subject), and when anything
     * but whitespace shares the parentheses with the condition.
     */
    private fun foldConditionParens(
        children: List<ChildEntry>,
        frameType: WNodeType,
    ): List<ChildEntry> {
        if (frameType !in CONDITION_PAREN_TYPES) return children
        val conditionIdx = children.indexOfFirst { it.type == WNodeType.CONDITION }
        if (conditionIdx < 0) return children
        val lparIdx = (conditionIdx - 1 downTo 0).firstOrNull { children[it].type == WNodeType.LPAR } ?: return children
        val rparIdx = (conditionIdx + 1 until children.size)
            .firstOrNull { children[it].type == WNodeType.RPAR } ?: return children
        if ((lparIdx + 1 until rparIdx).any { it != conditionIdx && children[it].type != WNodeType.WHITE_SPACE }) {
            return children
        }
        val openGap = children[lparIdx + 1].takeIf { it.type == WNodeType.WHITE_SPACE }
        if (openGap !is ChildEntry.Ws && children[rparIdx - 1] !is ChildEntry.Ws) return children

        val lparDoc = resolveEntry(children[lparIdx])
        val rparDoc = resolveEntry(children[rparIdx])
        val parts = mutableListOf(lparDoc)
        if (openGap != null) parts.add(elidedWs(openGap))
        parts.add(resolveEntry(children[conditionIdx]))
        parts.add(wsBreakAt(children, rparIdx - 1, rparDoc.start, flat = ""))
        parts.add(rparDoc)

        val folded = ChildEntry.Resolved(
            WNodeType.CONDITION,
            Doc.Group(Doc.Concat(parts, lparDoc.start, rparDoc.end)),
        )
        val out = ArrayList<ChildEntry>(children.size)
        out.addAll(children.subList(0, lparIdx))
        out.add(folded)
        out.addAll(children.subList(rparIdx + 1, children.size))
        return out
    }

    /** A zero-width [Doc.Text] claiming a whitespace [entry]'s own span, for a gap left unrendered. */
    private fun elidedWs(entry: ChildEntry): Doc.Text = when (entry) {
        is ChildEntry.Ws -> Doc.Text("", entry.start, entry.start + entry.rawText.length)
        is ChildEntry.Resolved -> Doc.Text("", entry.doc.start, entry.doc.end)
    }

    /**
     * Whether any still-open enclosing frame is a [WNodeType.FUN] — an unbounded ancestor walk,
     * not just the direct parent.
     */
    private fun ancestorHasFun(frameType: WNodeType): Boolean =
        frameType == WNodeType.BLOCK && frames.any { it.type == WNodeType.FUN }

    /**
     * Converts a [WNodeType.SEMICOLON] that separates two statements on the same physical line
     * (or precedes the frame's own closing `}` on the same line) into a `HARD` break, dropping the
     * semicolon character itself — a provably-redundant statement separator once the line break
     * takes over that role (§5.3). Scoped to [SEMICOLON_BREAK_SCOPE] only, which structurally
     * excludes a [WNodeType.CLASS_BODY]'s own enum-entries-list terminator (never a direct
     * [WNodeType.BLOCK]/[WNodeType.WHEN] child) — that semicolon stays [NoSemicolonsRule]'s alone.
     * Bails (leaves the semicolon untouched) when a real newline already separates it from the next
     * code token, when nothing follows it at all, or when a comment sits directly after it.
     */
    private fun convertStatementSeparatorSemicolons(
        children: List<ChildEntry>,
        frameType: WNodeType,
    ): List<ChildEntry> {
        if (frameType !in SEMICOLON_BREAK_SCOPE) return children
        if (children.none { it.type == WNodeType.SEMICOLON }) return children

        val out = ArrayList<ChildEntry>(children.size)
        var i = 0
        while (i < children.size) {
            val entry = children[i]
            if (entry.type != WNodeType.SEMICOLON) {
                out.add(entry)
                i++
                continue
            }
            val gapIdx = i + 1
            val gapEntry = children.getOrNull(gapIdx)
            val nextReal = (gapIdx until
                children.size).map { children[it] }.firstOrNull { it.type != WNodeType.WHITE_SPACE }
            if (gapEntry is ChildEntry.Ws || nextReal == null || nextReal.type in COMMENT_TYPES) {
                out.add(entry)
                i++
                continue
            }
            val semicolonDoc = (entry as ChildEntry.Resolved).doc
            out.add(ChildEntry.Ws("\n", semicolonDoc.start))
            i = if (gapEntry != null && isPlainWhitespace(gapEntry)) gapIdx + 1 else gapIdx
        }
        return out
    }

    /**
     * A [OWN_LINE_FORCE_TYPES] frame whose braced body (between its own `{` and `}` — for
     * [WNodeType.WHEN] that excludes the `when (subject)` header preceding `{`) already spans
     * multiple lines (any child, post-semicolon-conversion, is itself forced multi-line, or a rule
     * edit already collected inside the body inserts a line break) gets a `HARD` break right after
     * that `{` and right before its own closing `}` when one isn't
     * already there — no code shares `{`'s line, and `}` never shares a line with the content
     * before it. The same happens to a [branchOfMultilineIf] body whatever its own content, so
     * every braced branch of an `if` that spans lines in the source is laid out alike. A no-op
     * for: a frame without its own `{`/`}` pair (a lambda's transparent [WNodeType.BLOCK]); an
     * entirely single-line body (this mechanism never decides fit, only reacts to content that is
     * already going to be multi-line); an enum [WNodeType.CLASS_BODY] that is, as a whole, still
     * single-line (`enum class Foo { A, B }` stays exactly as written).
     */
    private fun forceMultilineBraceGaps(
        children: List<ChildEntry>,
        frameType: WNodeType,
        branchOfMultilineIf: Boolean,
    ): List<ChildEntry> {
        if (frameType !in OWN_LINE_FORCE_TYPES) return children
        if (children.isEmpty() || children.last().type != WNodeType.RBRACE) return children
        val lbraceIdx = children.indexOfFirst { it.type == WNodeType.LBRACE }
        if (lbraceIdx < 0 || lbraceIdx >= children.size - 1) return children
        val body = children.subList(lbraceIdx, children.size)
        if (!branchOfMultilineIf && body.none { isForcedMultilineChild(it) } && !pendingMultilineEditIn(body)) {
            return children
        }

        val withHeadBreak = insertBreakAfter(children, anchorIdx = lbraceIdx)
        return insertBreakBefore(withHeadBreak, anchorIdx = withHeadBreak.size - 1)
    }

    /**
     * Whether the node being exited is the braced body of a `then`/`else` branch whose `if` chain
     * (`else if` links included) spans more than one source line.
     */
    private fun isBranchOfMultilineIf(ctx: WContext): Boolean {
        val ancestors = ctx.ancestors
        var i = ancestors.size - 1
        if (i < 1) return false
        val parent = ancestors.typeAt(i)
        if (parent != WNodeType.THEN && parent != WNodeType.ELSE) return false
        i--
        if (ancestors.typeAt(i) != WNodeType.IF) return false
        while (i >= 2 && ancestors.typeAt(i - 1) == WNodeType.ELSE && ancestors.typeAt(i - 2) == WNodeType.IF) i -= 2
        val newline = ctx.sourceText.indexOfChar('\n', ancestors.startOffsetAt(i))
        return newline in 0 until ancestors.endOffsetAt(i)
    }

    private fun pendingMultilineEditIn(body: List<ChildEntry>): Boolean {
        val plan = editPlan ?: return false
        val open = body.first() as? ChildEntry.Resolved ?: return false
        val close = body.last() as? ChildEntry.Resolved ?: return false
        return plan.hasMultilineEditIn(open.doc.end, close.doc.start)
    }

    private fun isForcedMultilineChild(entry: ChildEntry): Boolean = when (entry) {
        is ChildEntry.Ws -> entry.rawText.containsChar('\n')
        is ChildEntry.Resolved -> spansMultipleLines(entry.doc)
    }

    private fun insertBreakAfter(children: List<ChildEntry>, anchorIdx: Int): List<ChildEntry> {
        val nextIdx = anchorIdx + 1
        val next = children.getOrNull(nextIdx) ?: return children
        if (next is ChildEntry.Ws) return children
        val result = children.toMutableList()
        if (isPlainWhitespace(next)) {
            result[nextIdx] = ChildEntry.Ws("\n", (next as ChildEntry.Resolved).doc.start)
        } else {
            val anchorEnd = (children[anchorIdx] as ChildEntry.Resolved).doc.end
            result.add(nextIdx, ChildEntry.Ws("\n", anchorEnd))
        }
        return result
    }

    private fun insertBreakBefore(children: List<ChildEntry>, anchorIdx: Int): List<ChildEntry> {
        val prevIdx = anchorIdx - 1
        val prev = children.getOrNull(prevIdx) ?: return children
        if (prev is ChildEntry.Ws) return children
        val result = children.toMutableList()
        if (isPlainWhitespace(prev)) {
            result[prevIdx] = ChildEntry.Ws("\n", (prev as ChildEntry.Resolved).doc.start)
        } else {
            val prevEnd = (prev as ChildEntry.Resolved).doc.end
            result.add(anchorIdx, ChildEntry.Ws("\n", prevEnd))
        }
        return result
    }

    /**
     * A [WNodeType.PROPERTY]'s own [WNodeType.PROPERTY_ACCESSOR] children (`get()`/`set()`), if
     * any, always render one [Doc.Indent] level deeper than the property itself
     * ([resolvePropertyAccessorsFrame]) — this takes priority over the initializer handling below,
     * since an accessor can follow an initializer that resolves to `null` there. Otherwise: when
     * the value after its direct [WNodeType.EQ] child is already forced multi-line, moves it onto
     * its own line, one [Doc.Indent] level deeper. A `FUN`'s own expression-body initializer is a
     * different grammar production (`EQ` is a direct child of `FUN`, never `PROPERTY`) and is
     * untouched by this frame.
     */
    private fun resolvePropertyFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val children = adjustAnnotationTrailingGap(frame.children)
        val accessorIdx = children.indexOfFirst { it.type == WNodeType.PROPERTY_ACCESSOR }
        if (accessorIdx >= 0) return resolvePropertyAccessorsFrame(children, start, end, accessorIdx)
        val eqIdx = children.indexOfFirst { it.type == WNodeType.EQ }
        if (eqIdx < 0) return resolveBraceFrame(frame, start, end)
        return resolveAssignedValueFrame(
            children,
            WNodeType.PROPERTY,
            start,
            end,
            eqIdx,
        ) ?: resolveInitializerFrame(children, WNodeType.PROPERTY, start, end, eqIdx)
    }

    /**
     * The value after a declaration's or named argument's `=` at [eqIdx]: a call-like value
     * ([isCallLikeEntry]) or a multi-line `if`/`when`/`try` ([hugsAnchor]) becomes a
     * [GroupKind.FLUID] group ([resolveFluidValueFrame]); any other value that already starts on
     * its own line keeps that break and renders one indent level deeper; a same-line value is left
     * to [resolveBraceFrame].
     */
    private fun resolveInitializerFrame(
        children: List<ChildEntry>,
        frameType: WNodeType,
        start: Int,
        end: Int,
        eqIdx: Int,
    ): Doc {
        if (valueAfterAnchorIsCallLike(children, eqIdx) || valueAfterAnchorHugs(children, eqIdx)) {
            return resolveFluidValueFrame(children, frameType, start, end, eqIdx)
        }
        val gapIdx = eqIdx + 1
        val gapEntry = children.getOrNull(gapIdx)
        if (gapEntry !is ChildEntry.Ws) {
            return resolveBraceFrame(Frame(frameType).also { it.children.addAll(children) }, start, end)
        }
        val valueIdx = (gapIdx until
            children.size).firstOrNull { children[it].type != WNodeType.WHITE_SPACE }
            ?: return resolveBraceFrame(Frame(frameType).also { it.children.addAll(children) }, start, end)
        val headParts = normalizeChildren(children.subList(0, eqIdx + 1), frameType)
        val breakDoc = clampWs(gapEntry, newlineCount = 1)
        val tailParts = normalizeChildren(children.subList(valueIdx, children.size), frameType)
        val tailEnd = tailParts.lastOrNull()?.end ?: end
        val body = Doc.Indent(Doc.Concat(listOf(breakDoc) + tailParts, breakDoc.start, tailEnd))
        return Doc.Concat(headParts + listOf(body), start, end)
    }

    private fun resolveFunFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val children = adjustAnnotationTrailingGap(frame.children)
        val eqIdx = children.indexOfFirst { it.type == WNodeType.EQ }
        if (eqIdx < 0) return resolveBraceFrame(frame, start, end)
        return resolveInitializerFrame(children, WNodeType.FUN, start, end, eqIdx)
    }

    /**
     * A typealias joined onto one line, its own line breaks dropped ([flattenDoc]). Joining never
     * crosses a comment: the leading comment run and the gaps inside it keep their breaks, and a
     * typealias carrying a comment anywhere past that run takes [resolveBraceFrame] instead.
     */
    private fun resolveTypealiasFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val children = frame.children
        val bodyFrom = children.indexOfFirst { it.type != WNodeType.WHITE_SPACE && it.type !in ANY_COMMENT_TYPES }
        if (bodyFrom < 0 || (bodyFrom until children.size).any { carriesComment(children[it]) }) {
            return resolveBraceFrame(frame, start, end)
        }
        val parts = ArrayList<Doc>()
        for ((i, entry) in children.withIndex()) {
            when {
                entry is ChildEntry.Ws -> {
                    parts.add(
                        if (i < bodyFrom) {
                            resolveEntry(entry)
                        } else {
                            Doc.Text(" ", entry.start, entry.start + entry.rawText.length)
                        },
                    )
                }
                isPlainWhitespace(entry) -> {
                    val ws = (entry as ChildEntry.Resolved).doc
                    val prevType = children.getOrNull(i - 1)?.type
                    val nextType = children.getOrNull(i + 1)?.type
                    val decision = if (prevType != null && nextType != null) {
                        spacingDecision(WNodeType.TYPEALIAS, prevType, nextType)
                    } else {
                        " "
                    }
                    parts.add(Doc.Text(decision ?: " ", ws.start, ws.end))
                }
                i < bodyFrom -> parts.add(resolveEntry(entry))
                else -> parts.add(flattenDoc(resolveEntry(entry)))
            }
        }
        return Doc.Concat(parts, start, end)
    }

    /** Joins [doc] onto one line; never called on a subtree holding a comment ([carriesComment]). */
    private fun flattenDoc(doc: Doc): Doc = when (doc) {
        is Doc.Text -> doc
        is Doc.Break -> Doc.Text(doc.flat, doc.start, doc.end)
        is Doc.Concat -> Doc.Concat(doc.parts.map { flattenDoc(it) }, doc.start, doc.end)
        is Doc.Indent -> flattenDoc(doc.body)
        is Doc.Group -> flattenDoc(doc.body)
        is Doc.TrailingComma -> Doc.Text("", doc.start, doc.end)
    }

    private fun valueAfterAnchorIsCallLike(children: List<ChildEntry>, anchorIdx: Int): Boolean {
        val valueIdx = (anchorIdx + 1 until
            children.size).firstOrNull { children[it].type != WNodeType.WHITE_SPACE } ?: return false
        return isCallLikeEntry(children[valueIdx])
    }

    private fun valueAfterAnchorHugs(children: List<ChildEntry>, anchorIdx: Int): Boolean {
        val valueIdx = (anchorIdx + 1 until
            children.size).firstOrNull { children[it].type != WNodeType.WHITE_SPACE } ?: return false
        return hugsAnchor(children[valueIdx])
    }

    /**
     * An `if`/`when`/`try` value that is multi-line — brace-bodied in the source, or about to
     * become so through a collected rule edit — stays on its anchor's line (`x = if (c) {`).
     */
    private fun hugsAnchor(entry: ChildEntry): Boolean {
        if (entry !is ChildEntry.Resolved || entry.type !in HUGGING_VALUE_TYPES) return false
        if (spansMultipleLines(entry.doc)) return true
        return editPlan?.hasMultilineEditIn(entry.doc.start, entry.doc.end) == true
    }

    private fun isCallLikeEntry(entry: ChildEntry): Boolean =
        entry.type == WNodeType.CALL_EXPRESSION ||
            entry.type in CHAIN_LINK_TYPES ||
            (entry is ChildEntry.Resolved && entry.wrapsCallLike)

    /**
     * A call or dot/safe-access chain assigned right after [anchorIdx] (a property's or an
     * expression-bodied function's `=`) becomes a [GroupKind.FLUID] group: it joins the `=` line
     * when its first line fits there, and otherwise moves onto its own line one indent level
     * deeper — the same decision whether or not the source had a newline after the `=`.
     */
    private fun resolveFluidValueFrame(
        children: List<ChildEntry>,
        frameType: WNodeType,
        start: Int,
        end: Int,
        anchorIdx: Int,
    ): Doc {
        val valueIdx = (anchorIdx + 1 until children.size).first { children[it].type != WNodeType.WHITE_SPACE }
        val headParts = normalizeChildren(children.subList(0, anchorIdx + 1), frameType)
        val anchorEnd = (children[anchorIdx] as ChildEntry.Resolved).doc.end
        val breakDoc = wsBreakAt(children, anchorIdx + 1, anchorEnd, flat = " ")
        val tailParts = normalizeChildren(children.subList(valueIdx, children.size), frameType)
        val tailEnd = tailParts.lastOrNull()?.end ?: end
        val body = Doc.Concat(listOf(breakDoc) + tailParts, breakDoc.start, tailEnd)
        return Doc.Concat(headParts + listOf(Doc.Group(body, GroupKind.FLUID, indentWhenBroken = true)), start, end)
    }

    /**
     * A lambda literal is a [GroupKind.LAMBDA] group: `{` (or the `->` after its parameters) is
     * followed by a break, its body sits one indent level deeper, and another break precedes the
     * closing `}`. Both breaks are `SOFT` when the source has no newline there, so a lambda that
     * fits its line stays on it and one that overflows opens after `{`/`->` and closes on its own
     * line; a source newline there stays a `HARD` break (up to one blank line after the head, none
     * before `}`). An empty lambda, or one without its own `{`/`}` pair, falls through to
     * [resolveBraceFrame] unchanged.
     */
    private fun resolveFunctionLiteralFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val children = normalizeLambdaBraces(frame.children)
        val lastIdx = children.size - 1
        if (children.size < 2 || children[0].type != WNodeType.LBRACE || children[lastIdx].type != WNodeType.RBRACE) {
            return resolveBraceFrame(rebuildFrame(frame, children), start, end)
        }
        val arrowIdx = children.indexOfFirst { it.type == WNodeType.ARROW }
        val headAnchorIdx = if (arrowIdx >= 0) arrowIdx else 0
        val bodyStartIdx = (headAnchorIdx + 1 until lastIdx).firstOrNull {
            children[it].type != WNodeType.WHITE_SPACE && !isEffectivelyEmpty(children[it])
        } ?: return resolveBraceFrame(rebuildFrame(frame, children), start, end)
        val bodyEndIdx = (bodyStartIdx until lastIdx).last { children[it].type != WNodeType.WHITE_SPACE }

        val headParts = normalizeChildren(children.subList(0, headAnchorIdx + 1), WNodeType.FUNCTION_LITERAL)
        val headAnchorEnd = (children[headAnchorIdx] as ChildEntry.Resolved).doc.end
        val headBreak = lambdaGapBreak(children, headAnchorIdx + 1, bodyStartIdx, headAnchorEnd, maxNewlines = 2)
        val bodyParts = normalizeChildren(children.subList(bodyStartIdx, bodyEndIdx + 1), WNodeType.FUNCTION_LITERAL)
        val bodyEnd = bodyParts.last().end
        val tailBreak = lambdaGapBreak(children, bodyEndIdx + 1, lastIdx, bodyEnd, maxNewlines = 1)
        val rbraceDoc = resolveEntry(children[lastIdx])

        val lbraceDoc = headParts.first()
        val interior = headParts.subList(1, headParts.size) + listOf(headBreak) + bodyParts
        val indented = Doc.Indent(Doc.Concat(interior, interior.first().start, bodyEnd))
        return Doc.Group(Doc.Concat(listOf(lbraceDoc, indented, tailBreak, rbraceDoc), start, end), GroupKind.LAMBDA)
    }

    private fun lambdaGapBreak(
        children: List<ChildEntry>,
        gapFrom: Int,
        gapUntil: Int,
        fallback: Int,
        maxNewlines: Int,
    ): Doc.Break {
        for (i in gapFrom until gapUntil) {
            val entry = children[i]
            if (entry is ChildEntry.Ws) {
                val actual = entry.rawText.count { it == '\n' }
                return clampWs(entry, if (actual > maxNewlines) maxNewlines else actual)
            }
        }
        return wsBreakAt(children, gapFrom, fallback, flat = " ")
    }

    /**
     * The gap right before [accessorIdx] (the property's first [WNodeType.PROPERTY_ACCESSOR]),
     * and everything from there through the frame's own end, move one [Doc.Indent] level deeper
     * than the property's own header — an accessor's own line is never at the property's ambient
     * depth. A same-line accessor (no real newline in that gap) renders identically either way,
     * since [Doc.Indent] only changes where a following `HARD` break lands.
     */
    private fun resolvePropertyAccessorsFrame(
        children: List<ChildEntry>,
        start: Int,
        end: Int,
        accessorIdx: Int,
    ): Doc {
        val gapIdx = accessorIdx - 1
        val bodyFrom = if (gapIdx >= 0 && (children[gapIdx] is ChildEntry.Ws || isPlainWhitespace(children[gapIdx]))) {
            gapIdx
        } else {
            accessorIdx
        }
        val headParts = normalizeChildren(children.subList(0, bodyFrom), WNodeType.PROPERTY)
        val tailParts = normalizeChildren(children.subList(bodyFrom, children.size), WNodeType.PROPERTY)
        val tailStart = tailParts.firstOrNull()?.start ?: start
        val tailEnd = tailParts.lastOrNull()?.end ?: end
        val body = Doc.Indent(Doc.Concat(tailParts, tailStart, tailEnd))
        return Doc.Concat(headParts + listOf(body), start, end)
    }

    /**
     * Shared by [resolvePropertyFrame], [resolveBinaryFrame]'s assignment-operator case, and
     * [resolveWhenEntryFrame]'s own arrow, for the value found right after [anchorIdx]. A comment
     * there (nested ahead of the real value) always moves onto its own line, one indent level
     * deeper, reusing the source gap's own break. An `if`/`when`/`try` value
     * ([HUGGING_VALUE_TYPES]) becomes a group that indents when broken: a multi-line one
     * ([hugsAnchor]) is [GroupKind.FLUID] and stays on the anchor's line while its first line fits
     * there (`x = if (c) {`); a single-line one stays on the anchor's line while it fits whole and
     * moves onto its own line one indent level deeper otherwise. Returns `null` for every other
     * value, leaving the caller's own default in place.
     */
    private fun resolveAssignedValueFrame(
        children: List<ChildEntry>,
        frameType: WNodeType,
        start: Int,
        end: Int,
        anchorIdx: Int,
    ): Doc? {
        val valueIdx = (anchorIdx + 1 until
            children.size).firstOrNull { children[it].type != WNodeType.WHITE_SPACE } ?: return null
        val valueEntry = children[valueIdx]
        val isLeadingComment = valueEntry.type in COMMENT_TYPES
        if (!isLeadingComment && valueEntry.type !in HUGGING_VALUE_TYPES) return null

        val headParts = normalizeChildren(children.subList(0, anchorIdx + 1), frameType)
        val gapEntry = children.getOrNull(anchorIdx + 1)
        if (!isLeadingComment) {
            val anchorEnd = (children[anchorIdx] as ChildEntry.Resolved).doc.end
            val softBreak = wsBreakAt(children, anchorIdx + 1, anchorEnd, flat = " ")
            val valueParts = normalizeChildren(children.subList(valueIdx, children.size), frameType)
            val valueEnd = valueParts.lastOrNull()?.end ?: end
            val body = Doc.Concat(listOf(softBreak) + valueParts, softBreak.start, valueEnd)
            val kind = if (hugsAnchor(valueEntry)) GroupKind.FLUID else GroupKind.DEFAULT
            return Doc.Concat(headParts + listOf(Doc.Group(body, kind, indentWhenBroken = true)), start, end)
        }
        val breakDoc = when {
            gapEntry is ChildEntry.Ws -> clampWs(gapEntry, newlineCount = 1)
            gapEntry != null && isPlainWhitespace(gapEntry) -> {
                val ws = (gapEntry as ChildEntry.Resolved).doc
                Doc.Break(BreakKind.HARD, literal = "\n", start = ws.start, end = ws.end)
            }

            else -> {
                val anchorEnd = (children[anchorIdx] as ChildEntry.Resolved).doc.end
                Doc.Break(BreakKind.HARD, literal = "\n", start = anchorEnd, end = anchorEnd)
            }
        }
        val tailParts = normalizeChildren(children.subList(valueIdx, children.size), frameType)
        val tailEnd = tailParts.lastOrNull()?.end ?: end
        val body = Doc.Indent(Doc.Concat(listOf(breakDoc) + tailParts, breakDoc.start, tailEnd))
        return Doc.Concat(headParts + listOf(body), start, end)
    }

    /**
     * A [WNodeType.PREFIX_EXPRESSION]/[WNodeType.POSTFIX_EXPRESSION] (`-x`, `!x`, `x++`, `x!!`) is
     * always tight to its operand: every single-line whitespace child inside one of these two
     * frames collapses to nothing.
     */
    private fun resolveUnaryFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        frame.wrapsCallLike = frame.children.any { isCallLikeEntry(it) }
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
        entry ==
            null ||
            entry.type ==
            WNodeType.RBRACE ||
            entry.type ==
            WNodeType.LBRACE ||
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
                children
                    .toMutableList()
                    .also { it[1] = ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(desired, ws.start, ws.end)) }
            }

            desired == " " -> {
                val pos = (children[0] as ChildEntry.Resolved).doc.end
                children
                    .toMutableList()
                    .also { it.add(1, ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(" ", pos, pos))) }
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
                children.toMutableList().also {
                    it[gapIndex] = ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(desired, ws.start, ws.end))
                }
            }

            desired == " " -> {
                val pos = (children[lastIndex] as ChildEntry.Resolved).doc.start
                children
                    .toMutableList()
                    .also { it.add(lastIndex, ChildEntry.Resolved(WNodeType.WHITE_SPACE, Doc.Text(" ", pos, pos))) }
            }

            else -> children
        }
    }

    /**
     * The single choke point for horizontal-spacing normalization on every frame [resolveFrame]
     * doesn't already give a dedicated `Group`/break treatment to. For every gap between two
     * direct children — an actual single-line [WNodeType.WHITE_SPACE] child, or no child at all
     * (two tokens directly adjacent) — [spacingDecision] is asked for the correct rendering; `null`
     * preserves whatever was there verbatim, except when the gap is absent and the following child
     * is a [WNodeType.EOL_COMMENT], which always gets one inserted space. A real newline
     * ([ChildEntry.Ws]) is never touched for horizontal spacing here — its newline count is
     * normalized separately by [verticalGapNewlineCount]/[clampWs].
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
                children.getOrNull(i + 1)?.type ==
                WNodeType.SUPER_TYPE_LIST
            if (entry is ChildEntry.Ws) {
                if (nextIsSuperTypeList && children.getOrNull(i - 1)?.type == WNodeType.COLON) {
                    out.add(Doc.Text("", entry.start, entry.start + entry.rawText.length))
                    continue
                }
                val isFirstAfterLbrace = i == 1 && children.getOrNull(i - 1)?.type == WNodeType.LBRACE
                val newlineCount = verticalGapNewlineCount(
                    frameType,
                    children,
                    i,
                    isFirstAfterLbrace,
                    ancestorHasFun,
                    actual = entry.rawText.count { it == '\n' },
                )
                out.add(clampWs(entry, newlineCount))
                continue
            }
            if (isPlainWhitespace(entry)) {
                if (nextIsSuperTypeList && precedesSuperTypeListLead(children, i - 1)) {
                    val ws = (entry as ChildEntry.Resolved).doc
                    out.add(Doc.Text("", ws.start, ws.end))
                    continue
                }
                val prevType = children.getOrNull(i - 1)?.type
                val nextType = children.getOrNull(i + 1)?.type
                val decision =
                    if (prevType != null && nextType != null) spacingDecision(frameType, prevType, nextType) else null
                val ws = (entry as ChildEntry.Resolved).doc
                out.add(if (decision != null) Doc.Text(decision, ws.start, ws.end) else ws)
                continue
            }
            out.add(resolveEntry(entry))
            val next = children.getOrNull(i + 1)
            if (next != null && !isPlainWhitespace(next) && next !is ChildEntry.Ws) {
                val isSuperTypeListLead = suppressSuperTypeListLeadGap &&
                    next.type ==
                    WNodeType.SUPER_TYPE_LIST &&
                    precedesSuperTypeListLead(children, i)
                val decision = if (isSuperTypeListLead) null else spacingDecision(frameType, entry.type, next.type)
                val wantsSpace = decision == " " || (decision == null && next.type == WNodeType.EOL_COMMENT)
                if (wantsSpace) {
                    val pos = out.last().end
                    out.add(Doc.Text(" ", pos, pos))
                }
            }
        }
        return out
    }

    /**
     * Whether everything from the class's own `:` up to and including [lastIdx] is nothing but
     * single-line whitespace and [BLOCK_COMMENT_TYPES] comments — the run a
     * [WNodeType.SUPER_TYPE_LIST]'s own lead break already writes the gap for, so the gap right
     * after [lastIdx] renders empty instead of doubling it. An [WNodeType.EOL_COMMENT] never
     * qualifies: it puts the supertype on a line of its own, and that gap is a real newline
     * [normalizeChildren] leaves alone.
     */
    private fun precedesSuperTypeListLead(
        children: List<ChildEntry>,
        lastIdx: Int,
    ): Boolean {
        var i = lastIdx
        while (i >= 0 && (isPlainWhitespace(children[i]) || children[i].type in BLOCK_COMMENT_TYPES)) i--
        return i >= 0 && children[i].type == WNodeType.COLON
    }

    /**
     * Decides the exact newline count for a whitespace gap: at most one blank line anywhere
     * (handled by the final fallback below), except zero blank lines right before a `BLOCK`/
     * `CLASS_BODY`/`WHEN`/`FUNCTION_LITERAL`'s own closing `}` (handled directly at
     * [resolveBraceFrame]'s own dedent call site, not here), zero blank lines immediately after a
     * [WNodeType.CLASS_BODY]'s own `{` or a [WNodeType.BLOCK]'s own `{` when some enclosing frame
     * is a [WNodeType.FUN] ([ancestorHasFun]), and zero blank lines between a class name and its
     * primary constructor ([lastNonCommentEntry] skips over an intervening trailing comment to
     * still find the identifier); exactly one blank line — gated on [entryHasContent] — between a
     * non-empty package directive and a non-empty import list ([firstNonCommentEntry] likewise
     * skips over a comment sitting directly after the package directive to still find that import
     * list), and between that import list and whatever follows it; exactly one blank line — the
     * other case that can add a newline, not just cap one — wherever [forcesDeclarationBlankLine]
     * applies.
     */
    private fun verticalGapNewlineCount(
        frameType: WNodeType,
        children: List<ChildEntry>,
        index: Int,
        isFirstAfterLbrace: Boolean,
        ancestorHasFun: Boolean,
        actual: Int,
    ): Int {
        val prevEntry = children.getOrNull(index - 1)
        val nextEntry = children.getOrNull(index + 1)
        if (frameType == WNodeType.FILE) {
            if (prevEntry?.type == WNodeType.PACKAGE_DIRECTIVE && entryHasContent(prevEntry)) {
                val nextReal = firstNonCommentEntry(children, index + 1)
                if (nextReal?.type == WNodeType.IMPORT_LIST && entryHasContent(nextReal)) {
                    return 2
                }
            }
            if (prevEntry?.type == WNodeType.IMPORT_LIST && nextEntry != null && entryHasContent(prevEntry)) {
                return 2
            }
        }
        if (isFirstAfterLbrace &&
            (frameType == WNodeType.CLASS_BODY || (frameType == WNodeType.BLOCK && ancestorHasFun))) {
            return 1
        }
        if (frameType == WNodeType.CLASS && nextEntry?.type == WNodeType.PRIMARY_CONSTRUCTOR) {
            val prevReal = lastNonCommentEntry(children, index - 1)
            if (prevReal?.type == WNodeType.IDENTIFIER) return 1
        }
        if (forcesDeclarationBlankLine(frameType, prevEntry, nextEntry, isFirstAfterLbrace)) {
            return 2
        }
        return if (actual > 2) 2 else actual
    }

    /**
     * The first entry at or after [fromIdx] that is neither plain whitespace nor
     * [COMMENT_TYPES] — walking forward past an intervening comment (and its own surrounding
     * whitespace) to find the real next structural sibling.
     */
    private fun firstNonCommentEntry(
        children: List<ChildEntry>,
        fromIdx: Int,
    ): ChildEntry? = (fromIdx until children.size)
        .asSequence()
        .map { children[it] }
        .firstOrNull { it.type != WNodeType.WHITE_SPACE && it.type !in COMMENT_TYPES }

    /**
     * The first entry at or before [uptoIdx] that is neither plain whitespace nor
     * [COMMENT_TYPES] — walking backward past an intervening comment (and its own surrounding
     * whitespace) to find the real previous structural sibling.
     */
    private fun lastNonCommentEntry(
        children: List<ChildEntry>,
        uptoIdx: Int,
    ): ChildEntry? = (uptoIdx downTo
        0)
        .asSequence()
        .map { children[it] }
        .firstOrNull { it.type != WNodeType.WHITE_SPACE && it.type !in COMMENT_TYPES }

    /**
     * Whether the gap right before [nextEntry] must carry at least one blank line, folding three
     * concerns into the one gap decision every [ChildEntry.Ws] already goes through: a
     * [WNodeType.CLASS]/[WNodeType.CLASS_INITIALIZER]/[WNodeType.FUN]/[WNodeType.OBJECT_DECLARATION]/
     * [WNodeType.PROPERTY] preceded by another declaration ([BLANK_LINE_BEFORE_DECLARATION_TYPES]);
     * any [DECLARATION_SPACING_TYPES] member carrying its own leading annotation or leading comment
     * ([ChildEntry.Resolved.hasLeadingAnnotation]/[ChildEntry.Resolved.hasLeadingComment]) — a wider
     * type set, with none of the base rule's own carve-outs below — when it follows another
     * [DECLARATION_SPACING_TYPES] member; and, inside a [WNodeType.PROPERTY]'s own frame, a leading-
     * annotated [WNodeType.PROPERTY_ACCESSOR] that follows another accessor.
     *
     * The first two only ever apply inside [DECLARATION_GAP_CONTAINER_TYPES] — a container without a
     * preceding sibling declaration (the very first member, or the very first file declaration) never
     * matches, since [prevEntry] then carries no declaration type at all. The base rule additionally
     * exempts: the first member right after a class body's or any block's own `{` ([isFirstAfterLbrace],
     * unconditional here — unlike [ancestorHasFun]'s narrower scope above, this exemption holds for
     * every block, not only a function's own); a [WNodeType.PROPERTY] directly inside a
     * [WNodeType.BLOCK] (a local variable, never forced); and two consecutive
     * [WNodeType.PROPERTY]s (in either container). Neither exemption applies to the annotation/comment
     * branch, matching how those are structurally separate, wider-scoped decisions.
     *
     * The comment/annotation branch relies on [ChildEntry.Resolved.hasLeadingComment]/
     * [hasLeadingAnnotation], which are true only when the comment/annotation is nested as the
     * declaration's own first child — the shape every declaration kind gets from a real compile
     * when no blank line separates it from what precedes, with one confirmed exception: a
     * [WNodeType.PROPERTY] directly inside a [WNodeType.BLOCK] (a local variable) does not nest an
     * immediately preceding comment as its own child at all; the comment surfaces as [WNodeType.BLOCK]'s
     * own sibling entry instead, so this branch cannot see it there and no blank line is forced —
     * unreachable by construction, not an oversight.
     */
    private fun forcesDeclarationBlankLine(
        frameType: WNodeType,
        prevEntry: ChildEntry?,
        nextEntry: ChildEntry?,
        isFirstAfterLbrace: Boolean,
    ): Boolean {
        val next = nextEntry as? ChildEntry.Resolved ?: return false
        val prevType = (prevEntry as? ChildEntry.Resolved)?.type

        if (frameType == WNodeType.PROPERTY) {
            return next.type == WNodeType.PROPERTY_ACCESSOR &&
                next.hasLeadingAnnotation &&
                prevType == WNodeType.PROPERTY_ACCESSOR
        }
        if (frameType !in DECLARATION_GAP_CONTAINER_TYPES) return false

        if (next.type in
            DECLARATION_SPACING_TYPES &&
            prevType in
            DECLARATION_SPACING_TYPES &&
            (next.hasLeadingAnnotation || next.hasLeadingComment)
        ) {
            return true
        }

        if (next.type !in BLANK_LINE_BEFORE_DECLARATION_TYPES) return false
        if (isFirstAfterLbrace) return false
        if (next.type == WNodeType.PROPERTY && (frameType == WNodeType.BLOCK || prevType == WNodeType.PROPERTY)) {
            return false
        }
        return prevType in DECLARATION_SPACING_TYPES
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
            entry.rawText.subSequence(0, entry.rawText.lastIndexOfChar('\n') + 1)
        } else {
            "\n".repeat(newlineCount)
        }
        return Doc.Break(BreakKind.HARD, literal = literal, start = entry.start, end = end)
    }

    private fun isPlainWhitespace(entry: ChildEntry): Boolean =
        entry is ChildEntry.Resolved && entry.type == WNodeType.WHITE_SPACE

    /** Whether [entry] is a comment or holds one anywhere below it ([ChildEntry.Resolved.carriesComment]). */
    private fun carriesComment(entry: ChildEntry): Boolean =
        entry.type in ANY_COMMENT_TYPES || (entry is ChildEntry.Resolved && entry.carriesComment)

    /**
     * The gap a [BLOCK_COMMENT_TYPES] comment keeps to the token beside it on the same line, for a
     * frame that rebuilds its own interior and would otherwise drop the whitespace written there:
     * none right after the `(` it opens from, none before the `,` or `)` that follows it, one
     * space against anything else. `null` when neither side is such a comment — the caller keeps
     * whatever it would have written.
     */
    private fun blockCommentGap(
        prevType: WNodeType?,
        nextType: WNodeType?,
    ): String? {
        if (prevType == null || nextType == null) return null
        if (prevType !in BLOCK_COMMENT_TYPES && nextType !in BLOCK_COMMENT_TYPES) return null
        if (prevType == WNodeType.LPAR) return ""
        if (nextType == WNodeType.COMMA || nextType == WNodeType.RPAR) return ""
        return " "
    }

    /**
     * The horizontal-spacing table for a gap between [prevType] and [nextType] inside [frameType]:
     * no space before a comma, one space after (none before a closing delimiter); colon spacing
     * keyed on the enclosing declaration ([COLON_WANTS_SPACE_BOTH_SIDES] — one space both sides —
     * versus the default type-annotation colon — none before, one after — and no space at all for
     * an annotation use-site-target colon); one space after `if`/`when`/`for`/`while`/`catch` and
     * before `where` ([KEYWORDS_WANTING_SPACE_AFTER]); a spread operator's `*` tight to its
     * argument; no space just inside `(`/`)`/`[`/`]`; none between a name and its parameter or
     * argument list, except a `FUNCTION_TYPE`'s or a `FUNCTION_LITERAL`'s own parameter list (the
     * latter's gap from `{` is [normalizeLambdaHead]'s concern); none between a `PROPERTY_ACCESSOR`'s
     * own `get`/`set` and its own parameter list either — some Kotlin compiler versions never wrap a
     * `get`/`set`'s own parameter list in a dedicated `VALUE_PARAMETER_LIST` node at all when it has
     * zero or one parameter (its `LPAR`/`RPAR` sit as bare `PROPERTY_ACCESSOR` children instead), so
     * this is checked as its own, version-shape-independent rule rather than folded into the
     * `VALUE_PARAMETER_LIST` one above; no space just inside `<`/`>` when the enclosing frame is a
     * [WNodeType.TYPE_PARAMETER_LIST]/[WNodeType.TYPE_ARGUMENT_LIST]; `::` tight after always;
     * `..`/`..<` tight both sides; no space before `?`.
     *
     * `null` means preserve whatever was there verbatim — every pair this table doesn't recognize.
     */
    private fun spacingDecision(
        frameType: WNodeType,
        prevType: WNodeType,
        nextType: WNodeType,
    ): String? {
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
            frameType !=
            WNodeType.FUNCTION_TYPE &&
            frameType !=
            WNodeType.FUNCTION_LITERAL
        ) {
            return ""
        }
        if (frameType ==
            WNodeType.PROPERTY_ACCESSOR &&
            (prevType == WNodeType.KW_GET || prevType == WNodeType.KW_SET) &&
            nextType ==
            WNodeType.LPAR
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

        if (nextType == WNodeType.DOT || prevType == WNodeType.DOT) return ""
        if (nextType == WNodeType.SAFE_ACCESS || prevType == WNodeType.SAFE_ACCESS) return ""

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
     *
     * A chain holding [CHAIN_LINK_BREAK_THRESHOLD] or more call links ([chainCallLinks]) is a
     * [GroupKind.CHAIN] group instead of a [GroupKind.CONTINUATION] one: one link's multi-line
     * lambda body then breaks every link onto its own line rather than leaving `}` joined to the
     * `.` after it. A chain with fewer call links keeps a lambda and the links around it together.
     *
     * A chain with no method call anywhere (`a.b.c`) is normally collapsed onto one line, dropping
     * its own whitespace; a chain carrying a comment of any kind in any of its links
     * ([Frame.hasChainComment], propagated up the links) never collapses and takes the break-splicing
     * path instead, so the whitespace the author wrote around that comment survives exactly as a
     * chain that does call something keeps it. [spliceBreak]'s break after an
     * [WNodeType.EOL_COMMENT] is `HARD`; after a block comment it stays `SOFT`, so such a chain
     * still renders on one line whenever it fits.
     *
     * When the receiver is a raw multi-line string and the whole expression is exactly
     * `<receiver>.trimIndent()` ([substituteTrimIndentReceiver]), the receiver's resolved `Doc` is
     * swapped for its [ChildEntry.Resolved.reindentedRawString] candidate before flattening.
     */
    private fun resolveChainFrame(
        frame: Frame,
        start: Int,
        end: Int,
        isRoot: Boolean,
    ): Doc {
        val children = substituteTrimIndentReceiver(frame.children)
        val opIdx = children.indexOfFirst { it.type == WNodeType.DOT || it.type == WNodeType.SAFE_ACCESS }
        if (opIdx < 0) return Doc.Concat(children.map { resolveEntry(it) }, start, end)
        val receiverEntry = children.first { it.type != WNodeType.WHITE_SPACE }
        frame.endsWithCallWithArguments =
            isCallWithArgumentsEntry(children.lastOrNull { it.type != WNodeType.WHITE_SPACE })
        frame.chainHeadIsRawString =
            receiverEntry.type == WNodeType.STRING_TEMPLATE ||
            (receiverEntry is ChildEntry.Resolved &&
                receiverEntry.type in CHAIN_LINK_TYPES &&
                receiverEntry.chainHeadIsRawString)
        frame.hasChainComment = children.any {
            it.type in ANY_COMMENT_TYPES ||
                (it is ChildEntry.Resolved && it.type in CHAIN_LINK_TYPES && it.hasChainComment)
        }
        frame.chainCallLinks = chainCallLinks(children, receiverEntry)

        if (!receiverHasMethodCall(children, opIdx)) {
            val hasCallAnywhere = children.any { it.type == WNodeType.CALL_EXPRESSION }
            if (!hasCallAnywhere) {
                frame.isQualifiedNameChain = true
            }
            val receiverType = (0 until
                opIdx).firstOrNull { children[it].type != WNodeType.WHITE_SPACE }?.let { children[it].type }
            val shouldCollapse = receiverType != WNodeType.STRING_TEMPLATE &&
                !frame.hasChainComment &&
                (isRoot || !hasCallAnywhere)
            if (shouldCollapse) {
                val collapsed = children.mapNotNull { entry ->
                    if (entry.type == WNodeType.WHITE_SPACE) null else resolveEntry(entry)
                }
                return Doc.Concat(collapsed, start, end)
            }
        }

        val parts = spliceBreak(
            children,
            anchorIndex = opIdx,
            breakBefore = true,
            flat = "",
            spreadTypes = CHAIN_LINK_TYPES,
        )
        val breaksEveryLink = frame.chainCallLinks >= CHAIN_LINK_BREAK_THRESHOLD
        return if (isRoot) {
            wrapRoot(
                parts,
                start,
                end,
                foldHead = frame.chainHeadIsRawString,
                kind = if (breaksEveryLink) GroupKind.CHAIN else GroupKind.CONTINUATION,
            )
        } else {
            Doc.Concat(parts, start, end)
        }
    }

    /**
     * How many links of this chain call something — this link's own selector plus whatever its
     * receiver link already counted. A call standing at the chain's head (`Join(users).join(..)`)
     * is the receiver, not a link, and is never counted.
     */
    private fun chainCallLinks(children: List<ChildEntry>, receiverEntry: ChildEntry): Int {
        val selector = children.lastOrNull { it.type != WNodeType.WHITE_SPACE }
        val receiverLinks = if (receiverEntry is ChildEntry.Resolved && receiverEntry.type in CHAIN_LINK_TYPES) {
            receiverEntry.chainCallLinks
        } else {
            0
        }
        return receiverLinks + if (selector?.type == WNodeType.CALL_EXPRESSION) 1 else 0
    }

    private fun receiverHasMethodCall(children: List<ChildEntry>, opIdx: Int): Boolean {
        for (i in 0 until opIdx) {
            val entry = children[i]
            if (entry.type == WNodeType.CALL_EXPRESSION) return true
            if (entry.type in CHAIN_LINK_TYPES && entry is ChildEntry.Resolved && !entry.isQualifiedNameChain) {
                return true
            }
        }
        return false
    }

    /**
     * Replaces this chain link's receiver (always its first child) with
     * [ChildEntry.Resolved.reindentedRawString] when [children] is exactly a raw string literal
     * followed by `.`/`?.` and a no-argument `trimIndent()` call — the one shape
     * [buildReindentedRawString] guarantees is value-preserving. Returns [children] unchanged
     * otherwise.
     */
    private fun substituteTrimIndentReceiver(children: List<ChildEntry>): List<ChildEntry> {
        val real = children.filter { it.type != WNodeType.WHITE_SPACE }
        if (real.size != 3) return children
        val receiver = real[0] as? ChildEntry.Resolved ?: return children
        if (receiver.type != WNodeType.STRING_TEMPLATE) return children
        if (real[1].type != WNodeType.DOT && real[1].type != WNodeType.SAFE_ACCESS) return children
        val call = real[2] as? ChildEntry.Resolved ?: return children
        if (call.type != WNodeType.CALL_EXPRESSION || flatText(call.doc) != "trimIndent()") return children
        val reindented = receiver.reindentedRawString ?: return children
        return children.mapIndexed { i, entry -> if (i == 0) ChildEntry.Resolved(entry.type, reindented) else entry }
    }

    /**
     * Nested [WNodeType.BINARY_EXPRESSION]s (`a + b + c`) flatten the same way [resolveChainFrame]
     * does: only the outermost expression wraps in [Doc.Group]/[Doc.Indent]. The break sits after
     * the operator for every operator but `?:`, which breaks before it, alongside `.`/`?.`. The
     * flat-form gap is one space for every operator except the range operator (`..`), which is
     * tight both sides, unconditionally.
     *
     * An assignment operator ([ASSIGNMENT_OPERATOR_TEXTS], e.g. `x = <expr>`, `x += <expr>`) is
     * never chained (never a [WNodeType.BINARY_EXPRESSION] operand of another one), so it is
     * always effectively root; an `if`/`when`/`try` value is placed by
     * [resolveAssignedValueFrame] instead of the generic chain/binary machinery below.
     */
    private fun resolveBinaryFrame(
        frame: Frame,
        start: Int,
        end: Int,
        isRoot: Boolean,
    ): Doc {
        val children = frame.children
        val opIdx = children.indexOfFirst { it.type == WNodeType.OPERATION_REFERENCE }
        if (opIdx < 0) return Doc.Concat(children.map { resolveEntry(it) }, start, end)

        val opText = flatText((children[opIdx] as ChildEntry.Resolved).doc)
        if (opText in ASSIGNMENT_OPERATOR_TEXTS) {
            resolveAssignedValueFrame(children, WNodeType.BINARY_EXPRESSION, start, end, opIdx)?.let { return it }
        }
        val isElvis = opText == "?:"
        val isLogical = opText == "&&" || opText == "||"
        val flat = if (opText == "..") "" else " "
        if (!isRoot && !isLogical && !isElvis) {
            return Doc.Concat(normalizeChildren(children, WNodeType.BINARY_EXPRESSION), start, end)
        }
        val parts = spliceBreak(
            children,
            anchorIndex = opIdx,
            breakBefore = isElvis,
            flat = flat,
            spreadTypes = BINARY_SPREAD_TYPES,
        )
        val foldHead = children.first { it.type != WNodeType.WHITE_SPACE }.type == WNodeType.STRING_TEMPLATE
        return if (isRoot) wrapRoot(parts, start, end, foldHead) else Doc.Concat(parts, start, end)
    }

    /**
     * Lays out a root chain/binary expression's flattened [parts]: the first operand (everything
     * before the first break) stays where it is, and the rest forms one [kind]
     * ([GroupKind.CONTINUATION] or [GroupKind.CHAIN])
     * group that indents its continuation lines only when broken — so a multi-line first operand
     * leaves the chain's own layout alone and nothing renders one level too deep when the chain
     * stays flat. With [foldHead] (a raw-string first operand) the first operand joins the group
     * instead, so a multi-line string forces the chain broken and its continuation onto its own
     * line at the same depth as the string's re-indented content.
     */
    private fun wrapRoot(
        parts: List<Doc>,
        start: Int,
        end: Int,
        foldHead: Boolean,
        kind: GroupKind = GroupKind.CONTINUATION,
    ): Doc {
        val firstBreak = parts.indexOfFirst { it is Doc.Break }
        if (firstBreak <= 0 || foldHead) {
            return Doc.Group(Doc.Concat(parts, start, end), kind, indentWhenBroken = true)
        }
        val head = Doc.Concat(parts.subList(0, firstBreak), start, parts[firstBreak - 1].end)
        val rest = Doc.Concat(parts.subList(firstBreak, parts.size), parts[firstBreak].start, end)
        return Doc.Concat(listOf(head, Doc.Group(rest, kind, indentWhenBroken = true)), start, end)
    }

    /**
     * Renders identically to [resolveBraceFrame]'s own default for [WNodeType.STRING_TEMPLATE];
     * additionally computes [buildReindentedRawString]'s candidate onto [Frame.reindentedRawString]
     * for [substituteTrimIndentReceiver] to read back once this node's enclosing chain (if any) is
     * resolved.
     */
    private fun resolveStringTemplateFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        frame.reindentedRawString = buildReindentedRawString(frame.children, start, end)
        return resolveBraceFrame(frame, start, end)
    }

    /**
     * Builds a re-indentable candidate for a raw multi-line string whose only children are
     * [WNodeType.OPEN_QUOTE]/[WNodeType.CLOSING_QUOTE] and [WNodeType.LITERAL_STRING_TEMPLATE_ENTRY]
     * (no interpolation) and whose shape already guarantees `trimIndent()` is value-preserving under
     * re-indentation: the first line is blank (content already starts on its own line), the last
     * line is blank (the closing quotes already sit on their own line), and at least one real,
     * non-blank content line exists. Every maximal run of consecutive entries equal to `"\n"`
     * becomes one [Doc.Break] carrying that many newlines in its own literal, so [Layout] emits
     * ambient-depth indent exactly once per run — right before whatever follows it — rather than
     * once per newline, which would otherwise plant indent characters on an interior blank line
     * that must stay empty; each real content line has its original common leading-whitespace
     * prefix (the exact prefix length `trimIndent()` itself would strip) folded into the preceding
     * break's elided tail, keeping only the content beyond that prefix as its own [Doc.Text] —
     * never changing what `trimIndent()` computes, only where the shared prefix physically sits.
     * An interior whitespace-only line makes the whole string ineligible: `trimIndent()` keeps
     * such a line's residual spaces beyond the stripped prefix, so its content is significant and
     * no re-indentation of it is value-preserving. Returns `null` for any other shape, including a
     * single content line whose common indent is already zero.
     */
    private fun buildReindentedRawString(
        children: List<ChildEntry>,
        start: Int,
        end: Int,
    ): Doc? {
        if (children.size < 3) return null
        val openEntry = children.first() as? ChildEntry.Resolved ?: return null
        val closeEntry = children.last() as? ChildEntry.Resolved ?: return null
        if (openEntry.type != WNodeType.OPEN_QUOTE || closeEntry.type != WNodeType.CLOSING_QUOTE) return null
        if (flatText(openEntry.doc) != "\"\"\"") return null

        val interior = children.subList(1, children.size - 1)
        if (interior.isEmpty() ||
            interior.any { it !is ChildEntry.Resolved || it.type != WNodeType.LITERAL_STRING_TEMPLATE_ENTRY }
        ) {
            return null
        }
        val entries = interior.map { it as ChildEntry.Resolved }
        val texts = entries.map { flatText(it.doc) }
        if (texts.any { it != "\n" && it.contains('\n') }) return null
        if (texts.none { it == "\n" } || texts.first() != "\n") return null

        val lastIdx = texts.lastIndex
        val last = texts[lastIdx]
        val closingTailIdx = when {
            last == "\n" -> null
            last.isNotEmpty() && last.isBlank() && lastIdx > 0 && texts[lastIdx - 1] == "\n" -> lastIdx
            else -> return null
        }
        val bodyLastIdx = closingTailIdx?.minus(1) ?: lastIdx

        if ((0..bodyLastIdx).any { texts[it] != "\n" && texts[it].isBlank() }) return null
        val contentTexts = (0..bodyLastIdx).map { texts[it] }.filter { it != "\n" && it.isNotBlank() }
        if (contentTexts.isEmpty()) return null
        val commonIndent = contentTexts.minOf { line ->
            line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
        }
        if (commonIndent <= 0) return null

        val out = ArrayList<Doc>(entries.size + 2)
        out.add(openEntry.doc)
        var i = 0
        while (i <= bodyLastIdx) {
            val entryDoc = entries[i].doc
            val text = texts[i]
            if (text != "\n") {
                out.add(entryDoc)
                i++
                continue
            }
            var runEnd = i
            while (runEnd + 1 <= bodyLastIdx && texts[runEnd + 1] == "\n") runEnd++
            val literal = "\n".repeat(runEnd - i + 1)
            val nextIdx = runEnd + 1
            when {
                nextIdx > bodyLastIdx -> {
                    val tailEnd = closingTailIdx?.let { entries[it].doc.end } ?: entries[runEnd].doc.end
                    out.add(Doc.Break(BreakKind.HARD, literal = literal, start = entryDoc.start, end = tailEnd))
                    i = nextIdx
                }

                else -> {
                    val nextDoc = entries[nextIdx].doc
                    out.add(
                        Doc.Break(
                            BreakKind.HARD,
                            literal = literal,
                            start = entryDoc.start,
                            end = nextDoc.start + commonIndent,
                        ),
                    )
                    out.add(Doc.Text(texts[nextIdx].drop(commonIndent), nextDoc.start + commonIndent, nextDoc.end))
                    i = nextIdx + 1
                }
            }
        }
        out.add(closeEntry.doc)
        return Doc.Concat(out, start, end)
    }

    /**
     * A call's argument list wraps in its own [Doc.Group]/[Doc.Indent], independent of any chain or
     * binary expression it sits inside: nested groups fit-check independently, so a short argument
     * list inside a long broken chain can still render flat. Break points: right after `(`, right
     * after every comma with another argument following it, and right before `)`. A pre-existing
     * trailing comma gets no break of its own, since the closing break already lands right after
     * it; its own text is replaced by [addDynamicTrailingComma]'s [Doc.TrailingComma], so its
     * presence in the rendered output follows this same [Doc.Group]'s own broken-vs-flat choice
     * rather than the source. A list holding nothing but comments gets no trailing comma at all,
     * and when the last thing inside the parentheses is a comment its own closing break doubles as
     * the list's, so `)` lands on the line right below it. A block comment keeps the gap
     * [blockCommentGap] asks for to whatever stands beside it, which the rebuilt interior — it
     * emits a gap only after `(`, after a comma and after an end-of-line comment — would otherwise
     * drop.
     */
    private fun resolveArgumentListFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
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
        frame.hasArguments = true
        val argumentCount = (lparIdx + 1 until rparIdx).count { children[it].type == WNodeType.VALUE_ARGUMENT }
        val nestsCallWithArguments = style.wrapNestedCallArguments &&
            templateEntryDepth == 0 &&
            (lparIdx + 1 until rparIdx).any { (children[it] as? ChildEntry.Resolved)?.isCallWithArguments == true }

        val lparDoc = resolveEntry(children[lparIdx])
        val rparDoc = resolveEntry(children[rparIdx])
        val trailingCommaIdx = trailingCommaIndex(children, lparIdx + 1, rparIdx)

        val huggedArgument = soleHuggableLambdaArgument(children, lparIdx, rparIdx, trailingCommaIdx)
        if (huggedArgument != null) {
            frame.hugsLambdaArgument = true
            return Doc.Concat(listOf(lparDoc, huggedArgument.doc, rparDoc), start, end)
        }

        val interior = ArrayList<Doc>()
        interior.add(wsBreakAt(children, lparIdx + 1, lparDoc.end, flat = ""))
        var prevType: WNodeType? = null
        var i = lparIdx + 1
        while (i < rparIdx) {
            val entry = children[i]
            if (entry.type == WNodeType.WHITE_SPACE || i == trailingCommaIdx) {
                i++
                continue
            }
            val entryDoc = resolveEntry(entry)
            if (entry.type == WNodeType.EOL_COMMENT && interior.lastOrNull() !is Doc.Break) {
                interior.add(Doc.Break(BreakKind.HARD, start = entryDoc.start, end = entryDoc.start))
            }
            if (interior.lastOrNull() !is Doc.Break && blockCommentGap(prevType, entry.type) == " ") {
                interior.add(Doc.Text(" ", entryDoc.start, entryDoc.start))
            }
            interior.add(entryDoc)
            if (entry.type == WNodeType.EOL_COMMENT) {
                interior.add(Doc.Break(BreakKind.HARD, start = entryDoc.end, end = entryDoc.end))
            } else if (entry.type == WNodeType.COMMA && hasNonWsBetween(children, i + 1, rparIdx)) {
                interior.add(wsBreakAt(children, i + 1, entryDoc.end, flat = " "))
            }
            prevType = entry.type
            i++
        }
        if (argumentCount > 0) addDynamicTrailingComma(interior, children, trailingCommaIdx)

        val lastInterior = interior.last()
        val closingBreak = if (lastInterior is Doc.Break && lastInterior.kind == BreakKind.HARD) {
            interior.removeAt(interior.size - 1)
            lastInterior
        } else {
            wsBreakAt(children, rparIdx - 1, rparDoc.start, flat = "")
        }
        val interiorDoc = Doc.Concat(interior, interior.first().start, interior.last().end)
        return Doc.Group(
            Doc.Concat(listOf(lparDoc, Doc.Indent(interiorDoc), closingBreak, rparDoc), start, end),
            GroupKind.ARGUMENTS,
            forceBreak = nestsCallWithArguments && argumentCount > 1,
            singleArgument = argumentCount == 1,
            forceNestedWhenBroken = nestsCallWithArguments && argumentCount == 1,
        )
    }

    private fun Int.isWs(children: List<ChildEntry>): Boolean = children[this].type == WNodeType.WHITE_SPACE

    /**
     * The one [WNodeType.VALUE_ARGUMENT] between `(` and `)` when it is the list's only content
     * besides whitespace and an optional trailing comma, and it is itself a bare lambda
     * ([ChildEntry.Resolved.hugsLambdaArgument]); `null` for every other argument list. Such an
     * argument hugs its parentheses — `foo({` / `})` — with no break, indent, or trailing comma of
     * the list's own, so the lambda body indents from the call's line exactly like a trailing lambda.
     */
    private fun soleHuggableLambdaArgument(
        children: List<ChildEntry>,
        lparIdx: Int,
        rparIdx: Int,
        trailingCommaIdx: Int?,
    ): ChildEntry.Resolved? {
        var sole: ChildEntry.Resolved? = null
        for (i in lparIdx + 1 until rparIdx) {
            val entry = children[i]
            if (entry.type == WNodeType.WHITE_SPACE || i == trailingCommaIdx) continue
            if (sole != null ||
                entry !is ChildEntry.Resolved ||
                entry.type != WNodeType.VALUE_ARGUMENT ||
                !entry.hugsLambdaArgument) {
                return null
            }
            sole = entry
        }
        return sole
    }

    private fun resolveCallExpressionFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        frame.isCallWithArguments = frame.children.any {
            it is ChildEntry.Resolved && it.type == WNodeType.VALUE_ARGUMENT_LIST && it.hasArguments
        }
        return resolveBraceFrame(frame, start, end)
    }

    private fun isCallWithArgumentsEntry(entry: ChildEntry?): Boolean =
        entry is ChildEntry.Resolved &&
            ((entry.type == WNodeType.CALL_EXPRESSION &&
                entry.isCallWithArguments) || (entry.type in CHAIN_LINK_TYPES && entry.endsWithCallWithArguments))

    private fun resolveValueArgumentFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val children = frame.children
        val real = children.filter { it.type != WNodeType.WHITE_SPACE }
        frame.hugsLambdaArgument = real.size == 1 && real[0].type == WNodeType.LAMBDA_EXPRESSION
        frame.isCallWithArguments = isCallWithArgumentsEntry(real.lastOrNull())
        val eqIdx = children.indexOfFirst { it.type == WNodeType.EQ }
        if (eqIdx < 0) return resolveBraceFrame(frame, start, end)
        return resolveInitializerFrame(children, WNodeType.VALUE_ARGUMENT, start, end, eqIdx)
    }

    private fun resolveSuperTypeCallEntryFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val last = frame.children.lastOrNull { it.type != WNodeType.WHITE_SPACE }
        frame.hugsLambdaArgument =
            last is ChildEntry.Resolved &&
            last.type == WNodeType.VALUE_ARGUMENT_LIST &&
            last.hugsLambdaArgument
        return resolveBraceFrame(frame, start, end)
    }

    /**
     * Index of the trailing comma in `children[fromIdx until closeIdx]` — a [WNodeType.COMMA]
     * followed by nothing but whitespace before `closeIdx` — or `null` if there is none.
     */
    private fun trailingCommaIndex(
        children: List<ChildEntry>,
        fromIdx: Int,
        closeIdx: Int,
    ): Int? = (fromIdx until closeIdx).lastOrNull {
        children[it].type == WNodeType.COMMA && !hasNonWsNonCommentBetween(children, it + 1, closeIdx)
    }

    /**
     * Appends [Doc.TrailingComma] right after [interior]'s last element when
     * [FormatStyle.trailingCommas] is enabled: its span reuses [trailingCommaIdx]'s original comma
     * when one already sits at the trailing position, or a zero-width point at the last element's
     * end otherwise. [Layout] alone decides whether it renders, from the enclosing [Doc.Group]'s
     * chosen mode — this is the comma-iff-broken mechanism for a fit-driven or threshold-forced
     * list, so a single call site covers both the always-broken and the fits-dependent case.
     */
    private fun addDynamicTrailingComma(
        interior: MutableList<Doc>,
        children: List<ChildEntry>,
        trailingCommaIdx: Int?,
    ) {
        if (!style.trailingCommas) return
        val anchor = interior.lastOrNull() ?: return
        val existing = trailingCommaIdx?.let { (children[it] as ChildEntry.Resolved).doc }
        interior.add(Doc.TrailingComma(existing?.start ?: anchor.end, existing?.end ?: anchor.end))
    }

    private fun hasNonWsBetween(
        children: List<ChildEntry>,
        from: Int,
        until: Int,
    ): Boolean = (from until until).any { children[it].type != WNodeType.WHITE_SPACE }

    private fun hasNonWsNonCommentBetween(
        children: List<ChildEntry>,
        from: Int,
        until: Int,
    ): Boolean = (from until
        until).any { children[it].type != WNodeType.WHITE_SPACE && children[it].type !in COMMENT_TYPES }

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
    private fun resolveValueParameterListFrame(
        frame: Frame,
        start: Int,
        end: Int,
        parentType: WNodeType?,
    ): Doc {
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
        val hasDefaultValue = paramIndices.size >= 2 &&
            paramIndices.any { "= " in flatText((children[it] as ChildEntry.Resolved).doc) }
        val forceMultiline = (threshold != null &&
            paramIndices.size >= threshold) ||
            hasDefaultValue ||
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
    private fun passthroughParameterList(
        children: List<ChildEntry>,
        start: Int,
        end: Int,
    ): Doc {
        if (children.isEmpty()) return Doc.Concat(emptyList(), start, end)

        val rparIdx = children.indexOfLast { it.type == WNodeType.RPAR }
        val closeIdx = if (rparIdx >= 0) rparIdx else children.size
        val adjusted = applyTrailingComma(children, 0, closeIdx)

        val lastIndex = adjusted.size - 1
        val dedentIndex = lastIndex - 1
        val opensIndentScope = adjusted[lastIndex].type == WNodeType.RPAR &&
            dedentIndex >= 0 &&
            adjusted[dedentIndex] is ChildEntry.Ws
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
     * Every direct child that is neither a comma nor whitespace is one supertype entry — a
     * `by`-delegated entry, which has no [WNodeType] of its own, included.
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
     * that line and every supertype, including the first, starts its own line. A pair of entries
     * that no comma separates bails to [resolveBraceFrame] too, before the lead gap is claimed.
     */
    private fun resolveSuperTypeListFrame(
        frame: Frame,
        start: Int,
        end: Int,
        parentType: WNodeType?,
    ): Doc {
        if (parentType != WNodeType.CLASS) return resolveBraceFrame(frame, start, end)
        val children = frame.children
        if (children.any { it.type in COMMENT_TYPES }) return resolveBraceFrame(frame, start, end)
        val entryIndices = children.indices.filter { children[it].type !in SUPER_TYPE_SEPARATOR_TYPES }
        if (entryIndices.isEmpty()) return resolveBraceFrame(frame, start, end)
        val commaIndices = entryIndices.zipWithNext().map { (a, b) ->
            (a + 1 until b).firstOrNull { children[it].type == WNodeType.COMMA }
                ?: return resolveBraceFrame(frame, start, end)
        }

        frames.lastOrNull()?.ownsSuperTypeListLeadGap = true

        val ctorWrapped = frames
            .lastOrNull()
            ?.children
            ?.firstOrNull { it.type == WNodeType.PRIMARY_CONSTRUCTOR }
            ?.let { it is ChildEntry.Resolved && spansMultipleLines(it.doc) } == true

        val entryDocs = entryIndices.map { resolveEntry(children[it]) }

        val soleEntryHugsLambda = (children[entryIndices[0]] as ChildEntry.Resolved).hugsLambdaArgument
        if (entryDocs.size == 1 && (ctorWrapped || soleEntryHugsLambda)) {
            return Doc.Concat(listOf(Doc.Text(" ", start, start), entryDocs[0]), start, end)
        }
        if (entryDocs.size == 1) {
            val anyMultilineEntry = spansMultipleLines((children[entryIndices[0]] as ChildEntry.Resolved).doc)
            val breakKind = if (anyMultilineEntry) BreakKind.HARD else BreakKind.SOFT
            val lead = Doc.Break(breakKind, flat = " ", start = start, end = start)
            return Doc.Group(Doc.Indent(Doc.Concat(listOf(lead, entryDocs[0]), start, end)))
        }

        if (ctorWrapped) {
            val body = ArrayList<Doc>()
            body.add(Doc.Text(" ", start, start))
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

        val body = ArrayList<Doc>()
        body.add(Doc.Break(BreakKind.SOFT, flat = " ", start = start, end = start))
        body.add(entryDocs[0])
        for (i in 1 until entryDocs.size) {
            val commaIdx = commaIndices[i - 1]
            val commaDoc = resolveEntry(children[commaIdx])
            body.add(commaDoc)
            body.add(wsBreakAt(children, commaIdx + 1, commaDoc.end, flat = " "))
            body.add(entryDocs[i])
        }
        return Doc.Group(Doc.Indent(Doc.Concat(body, start, end)))
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
     *
     * A [WNodeType.MODIFIER_LIST] carrying at least one [WNodeType.ANNOTATION_ENTRY] marks its still-
     * open enclosing frame's [Frame.hasLeadingAnnotation], regardless of the bail/wrap decision above
     * — [forcesDeclarationBlankLine] reads this once the enclosing declaration is itself resolved
     * into a [ChildEntry.Resolved].
     */
    private fun resolveAnnotationContainerFrame(
        frame: Frame,
        start: Int,
        end: Int,
        parentType: WNodeType?,
    ): Doc {
        if (parentType != null && parentType in ANNOTATION_EXEMPT_PARENT_TYPES) {
            return resolveBraceFrame(frame, start, end)
        }
        val children = frame.children
        if (children.any { it.type == WNodeType.UNKNOWN || it.type in COMMENT_TYPES }) {
            return resolveBraceFrame(frame, start, end)
        }
        val entryIndices = children.indices.filter { children[it].type == WNodeType.ANNOTATION_ENTRY }
        if (entryIndices.isEmpty()) return resolveBraceFrame(frame, start, end)
        if (frame.type == WNodeType.MODIFIER_LIST) {
            frames.lastOrNull()?.hasLeadingAnnotation = true
        }
        if (frame.type == WNodeType.ANNOTATED_EXPRESSION && isBeforeLambdaExpression(children, entryIndices.last())) {
            return resolveBraceFrame(frame, start, end)
        }

        val hasArgAnnotation = entryIndices.any { containsParen((children[it] as ChildEntry.Resolved).doc) }
        if (!hasArgAnnotation && entryIndices.size < 2) return resolveBraceFrame(frame, start, end)

        return wrapAnnotationEntries(children, entryIndices, frame.type, start, end)
    }

    private fun isBeforeLambdaExpression(children: List<ChildEntry>, lastEntryIdx: Int): Boolean {
        val next = (lastEntryIdx + 1 until
            children.size).map { children[it] }.firstOrNull { !isPlainWhitespace(it) && it !is ChildEntry.Ws }
        return next?.type == WNodeType.LAMBDA_EXPRESSION
    }

    private fun containsParen(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> doc.value.containsChar('(')
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
    private fun wrapAnnotationEntries(
        children: List<ChildEntry>,
        entryIndices: List<Int>,
        frameType: WNodeType,
        start: Int,
        end: Int,
    ): Doc {
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
        val suffixStart =
            if (gap != null && (gap is ChildEntry.Ws || isPlainWhitespace(gap))) lastEntry + 2 else lastEntry + 1
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
        if (children.none {
            it is ChildEntry.Resolved && it.type in ANNOTATION_CONTAINER_TYPES && endsWithHardBreak(it.doc)
        }) {
            return children
        }
        val out = ArrayList<ChildEntry>(children.size)
        var i = 0
        while (i < children.size) {
            val entry = children[i]
            out.add(entry)
            if (entry is ChildEntry.Resolved &&
                entry.type in ANNOTATION_CONTAINER_TYPES &&
                endsWithHardBreak(entry.doc)) {
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
    private fun resolveAngleListFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val closeIdx = frame.children.indexOfFirst { it.type == WNodeType.GT }
        if (closeIdx < 0) return resolveBraceFrame(frame, start, end)
        return resolveBraceFrame(rebuildFrame(frame, applyTrailingComma(frame.children, 0, closeIdx)), start, end)
    }

    /**
     * A [WNodeType.DESTRUCTURING_DECLARATION]'s own closing [WNodeType.RPAR] anchors
     * [applyTrailingComma]; the value after its own [WNodeType.EQ], when it has one, is placed by
     * the same [resolveAssignedValueFrame]/[resolveInitializerFrame] pair a [WNodeType.PROPERTY]
     * uses, so `val (a, b) = <value>` and `val a = <value>` wrap alike. A `for`-loop's own
     * destructuring has no `EQ` and falls through to [resolveBraceFrame].
     */
    private fun resolveDestructuringFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val closeIdx = frame.children.indexOfLast { it.type == WNodeType.RPAR }
        if (closeIdx < 0) return resolveBraceFrame(frame, start, end)
        val children = applyTrailingComma(frame.children, 0, closeIdx)
        val eqIdx = children.indexOfFirst { it.type == WNodeType.EQ }
        if (eqIdx < 0) return resolveBraceFrame(rebuildFrame(frame, children), start, end)
        return resolveAssignedValueFrame(
            children,
            WNodeType.DESTRUCTURING_DECLARATION,
            start,
            end,
            eqIdx,
        ) ?: resolveInitializerFrame(children, WNodeType.DESTRUCTURING_DECLARATION, start, end, eqIdx)
    }

    /**
     * A [WNodeType.WHEN_ENTRY]'s own [WNodeType.ARROW] anchors [applyTrailingComma] over its
     * condition list, bailing entirely for: an `else` entry; an entry whose enclosing `when` has
     * no parenthesized subject ([hasSubject]) — a subject-less entry's grammar has no comma
     * production at all, so inserting one would break compilation; or an entry containing a
     * structurally-unrecognized child ([WNodeType.UNKNOWN] — a guard clause has no [WNodeType] of
     * its own, so this is the only way to detect one). In all three cases the entry is left
     * untouched.
     *
     * Independent of that bail (it concerns only the condition list): an `if`/`when`/`try` body
     * after the arrow is placed by [resolveAssignedValueFrame], a multi-line one hugging the
     * arrow; a [WNodeType.BLOCK] body is owned by [forceMultilineBraceGaps] instead.
     */
    private fun resolveWhenEntryFrame(
        frame: Frame,
        start: Int,
        end: Int,
    ): Doc {
        val children = frame.children
        val arrowIdx = children.indexOfFirst { it.type == WNodeType.ARROW }
        val hasSubject = frames.lastOrNull()?.children?.any { it.type == WNodeType.LPAR } == true
        val bail = arrowIdx <
            0 ||
            !hasSubject ||
            (0 until arrowIdx).any { children[it].type == WNodeType.KW_ELSE || children[it].type == WNodeType.UNKNOWN }
        val adjusted = if (bail) children else applyTrailingComma(children, 0, arrowIdx)

        val adjustedArrowIdx = adjusted.indexOfFirst { it.type == WNodeType.ARROW }
        if (adjustedArrowIdx >= 0) {
            resolveAssignedValueFrame(adjusted, WNodeType.WHEN_ENTRY, start, end, adjustedArrowIdx)?.let { return it }
            return resolveInitializerFrame(adjusted, WNodeType.WHEN_ENTRY, start, end, adjustedArrowIdx)
        }
        return resolveBraceFrame(rebuildFrame(frame, adjusted), start, end)
    }

    private fun rebuildFrame(
        frame: Frame,
        children: List<ChildEntry>,
    ): Frame = Frame(frame.type).also { it.children.addAll(children) }

    /**
     * The static trailing-comma decision for a list [resolveBraceFrame] renders verbatim (never
     * reflowed): present when [FormatStyle.trailingCommas] is enabled and any child in
     * `children[fromIdx until closeIdx]` already spans multiple lines ([isMultilineEntry]), absent
     * otherwise — inserted or removed once, here, at build time (unlike
     * [addDynamicTrailingComma]'s per-render decision for a list the printer actually reflows).
     * `closeIdx` need not be a real delimiter's own index — a lambda's own parameter list has none
     * of its own, so callers pass `children.size` to mean "right after the last child".
     *
     * Only a real element ([isListElement]) counts, and the comma is placed right after the last
     * one: a list holding nothing but comments and brackets is left untouched, and a comma is
     * never written after a trailing comment.
     */
    private fun applyTrailingComma(
        children: List<ChildEntry>,
        fromIdx: Int,
        closeIdx: Int,
    ): List<ChildEntry> {
        if ((fromIdx until closeIdx).none { isListElement(children[it]) }) return children

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

    /**
     * Whether [entry] is one of a list's own elements — neither whitespace, nor a comma, nor a
     * comment, nor one of the brackets the list is written between.
     */
    private fun isListElement(entry: ChildEntry): Boolean =
        entry !is ChildEntry.Ws &&
            !isPlainWhitespace(entry) &&
            entry.type != WNodeType.COMMA &&
            entry.type !in COMMENT_TYPES &&
            entry.type !in LIST_BRACKET_TYPES

    private fun insertTrailingComma(children: List<ChildEntry>, closeIdx: Int): List<ChildEntry> {
        val lastContentIdx = (0 until closeIdx).lastOrNull { isListElement(children[it]) } ?: return children
        val anchor = (children[lastContentIdx] as ChildEntry.Resolved).doc.end
        val comma = ChildEntry.Resolved(WNodeType.COMMA, Doc.Text(",", anchor, anchor))
        return children.toMutableList().also { it.add(lastContentIdx + 1, comma) }
    }

    private fun removeTrailingComma(
        children: List<ChildEntry>,
        existingIdx: Int,
    ): List<ChildEntry> = children.toMutableList().also { it.removeAt(existingIdx) }

    private fun spansMultipleLines(doc: Doc): Boolean = when (doc) {
        is Doc.Text -> doc.lastNewline() >= 0
        is Doc.Break -> doc.kind == BreakKind.HARD
        is Doc.TrailingComma -> false
        is Doc.Indent -> spansMultipleLines(doc.body)
        is Doc.Group -> doc.forceBreak || spansMultipleLines(doc.body)
        is Doc.Concat -> doc.parts.any { spansMultipleLines(it) }
    }

    /**
     * Builds [children] with one `SOFT` [Doc.Break] spliced in at [anchorIndex] ([breakBefore] it
     * or after it), consuming the adjacent whitespace child in its place if one is there. Shared by
     * [resolveChainFrame] (break before the dot/safe-access operator) and [resolveBinaryFrame]
     * (break after the operator, or before it for `?:`). A child whose type is in [spreadTypes] —
     * an inner link of the same chain — contributes its own already-spliced parts directly, so the
     * root sees one flat run of operands and breaks; any other child is one opaque part.
     *
     * The gap on the other side of [anchorIndex] wants the same [flat] text but is never itself a
     * break candidate: a plain, single-line `WHITE_SPACE` there is normalized to [flat] directly; a
     * real newline on that side is left untouched.
     *
     * The spliced break is `SOFT` unless it follows an [WNodeType.EOL_COMMENT]
     * ([followsEolComment]), where it is `HARD`.
     */
    private fun spliceBreak(
        children: List<ChildEntry>,
        anchorIndex: Int,
        breakBefore: Boolean,
        flat: String,
        spreadTypes: WNodeTypeSet,
    ): List<Doc> {
        val wsIndex = if (breakBefore) anchorIndex - 1 else anchorIndex + 1
        val hasWs = wsIndex in children.indices && wsIndex.isWs(children)
        val insertIndex = if (breakBefore) anchorIndex else anchorIndex + 1
        val anchorDoc = (children[anchorIndex] as ChildEntry.Resolved).doc
        val fallback = if (breakBefore) anchorDoc.start else anchorDoc.end
        val breakKind = if (followsEolComment(children, wsIndex)) BreakKind.HARD else BreakKind.SOFT
        val breakDoc = wsBreakAt(children, wsIndex, fallback, flat, breakKind)

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
            val entry = children[idx]
            val doc = resolveEntry(entry)
            if (entry.type in spreadTypes && doc is Doc.Concat) parts.addAll(doc.parts) else parts.add(doc)
        }
        if (insertIndex == children.size) parts.add(breakDoc)
        return parts
    }

    /**
     * Whether the nearest non-whitespace child before [wsIndex] is a [WNodeType.EOL_COMMENT] —
     * everything after such a comment has to start on a new line, so the break there can never be
     * a fit decision.
     */
    private fun followsEolComment(children: List<ChildEntry>, wsIndex: Int): Boolean {
        var i = wsIndex - 1
        while (i >= 0 && children[i].type == WNodeType.WHITE_SPACE) i--
        return i >= 0 && children[i].type == WNodeType.EOL_COMMENT
    }

    /**
     * A [kind] [Doc.Break] (`SOFT` by default) at [wsIndex]: if [children] has a `WHITE_SPACE`-typed
     * child there, the break's span claims exactly the whitespace it replaces; otherwise a
     * zero-width break is anchored at [fallback]. [flat] always wins over whatever whitespace it
     * replaces.
     */
    private fun wsBreakAt(
        children: List<ChildEntry>,
        wsIndex: Int,
        fallback: Int,
        flat: String,
        kind: BreakKind = BreakKind.SOFT,
    ): Doc.Break {
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
        is Doc.Text -> doc.value.toString()
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
        var hasLeadingAnnotation = false
        var reindentedRawString: Doc? = null
        var isQualifiedNameChain = false
        var hugsLambdaArgument = false
        var wrapsCallLike = false
        var chainHeadIsRawString = false
        var hasArguments = false
        var isCallWithArguments = false
        var endsWithCallWithArguments = false
        var branchOfMultilineIf = false
        var hasChainComment = false
        var chainCallLinks = 0
    }

    private sealed interface ChildEntry {
        val type: WNodeType

        class Resolved(
            override val type: WNodeType,
            val doc: Doc,
            val hasLeadingAnnotation: Boolean = false,
            val hasLeadingComment: Boolean = false,
            val reindentedRawString: Doc? = null,
            val isQualifiedNameChain: Boolean = false,
            val hugsLambdaArgument: Boolean = false,
            val wrapsCallLike: Boolean = false,
            val chainHeadIsRawString: Boolean = false,
            val hasArguments: Boolean = false,
            val isCallWithArguments: Boolean = false,
            val endsWithCallWithArguments: Boolean = false,
            val hasChainComment: Boolean = false,
            val chainCallLinks: Int = 0,
            val carriesComment: Boolean = false,
        ) : ChildEntry

        class Ws(val rawText: CharSequence, val start: Int) : ChildEntry {
            override val type: WNodeType = WNodeType.WHITE_SPACE
        }
    }
}
