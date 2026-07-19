package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned, always-on `WStreamRule` (design.md §7, D8) that rides the same single walk as
 * every user rule, collecting every `@Suppress`/`@file:Suppress`/`@kotlin.Suppress` region into
 * [index] as it goes. Not user-configurable — [com.varlanv.wrasse.model.WRuleSet.dispatchForFile]'s
 * `alwaysOn` param injects one fresh instance per file regardless of which real rules are active.
 *
 * **Ordering property this design leans on:** an annotation entry always sits, syntactically,
 * before the content it can suppress (a modifier list precedes its declaration's body; a file
 * annotation list is the file's first construct; an annotated expression's own entry precedes its
 * base expression) — and the LightTree is already fully parsed before the walk starts, so an
 * ancestor's *final* `[start, end)` span is known the moment it is pushed (`WNodeStack.push`),
 * not just once its children are later visited. So by the time [SuppressionIndex.isSuppressed] is
 * consulted for *any* offset — whether from an ordinary leaf-driven report or a `WFileRule`/
 * `afterFile`-deferred one (the import engine) — every region that could cover it has already been
 * registered here. No two-phase pre-scan, no deferred re-filtering of already-collected reports:
 * the gate lives directly in `WReporter.report` (see `WrassePlugin.checkFile`), checked
 * synchronously against whatever this collector has accumulated so far.
 *
 * **Scope resolution (owner span):** at `ANNOTATION_ENTRY` enter, before it is pushed onto
 * [WContext.ancestors], the current top of that stack is the entry's own immediate parent:
 * `FILE_ANNOTATION_LIST` → file scope; `ANNOTATED_EXPRESSION` → that node's own span; `MODIFIER_LIST`
 * → the modifier list's *own* parent's span (the declaration/parameter/constructor it modifies, one
 * level further up the stack) — anything else (including the bracket `@[A B]` multi-annotation
 * form, whose wrapping `ANNOTATION` node is intentionally left unmapped) resolves no scope at all,
 * so such an entry is inert rather than wrongly wired — conservative, matching the "unknown entries
 * suppress nothing" stance for that unusual syntax.
 *
 * **Argument matching:** only a directly-written, non-interpolated string literal counts —
 * `VALUE_ARGUMENT`'s sole direct child must be a `STRING_TEMPLATE` whose own direct children are at
 * most one `LITERAL_STRING_TEMPLATE_ENTRY` (empty string, or plain text with no escape/interpolation
 * entries of any kind). Anything else — concatenation, a const reference, a named argument, an
 * escape sequence, string interpolation — is conservatively treated as non-literal and ignored,
 * never partially evaluated. The callee name is matched syntactically off `CONSTRUCTOR_CALLEE`'s
 * identifiers: simple `Suppress`, or exactly the two segments `kotlin.Suppress` — a user's own
 * class also named `Suppress` (or a differently-resolving `kotlin.Suppress` alias) would false-match
 * here too, since this is a syntactic check, not a resolved one (documented limitation, design.md §7).
 */
class SuppressionCollectorRule : WStreamRule {
    override val id = "suppress-collector"
    override val config = WrasseRuleConfig(level = RuleLevel.OFF, exclude = emptyList(), effectiveLevel = RuleLevel.OFF)

    val index = SuppressionIndex()

    private var entryActive = false
    private var entryScope: AnnotationScope = AnnotationScope.None
    private var calleeSegments = mutableListOf<String>()
    private var literalArgs = mutableListOf<String>()

    private var argChildTypes = mutableListOf<WNodeType>()
    private var argIsPlainStringLiteral = false
    private var argLiteralText: StringBuilder? = null
    private var trackingTemplate = false
    private var templateChildTypes = mutableListOf<WNodeType>()

    override fun visitLeaf(ctx: WContext, reporter: WReporter) {
        if (!entryActive) return
        val parent = if (ctx.ancestors.isEmpty) null else ctx.ancestors.peekType()
        if (ctx.type == WNodeType.IDENTIFIER && ctx.hasAncestor(WNodeType.CONSTRUCTOR_CALLEE)) {
            val text = ctx.leafText?.toString()?.removeSurrounding("`") ?: return
            calleeSegments.add(text)
            return
        }
        if (trackingTemplate && ctx.type == WNodeType.REGULAR_STRING_PART && parent == WNodeType.LITERAL_STRING_TEMPLATE_ENTRY) {
            argLiteralText?.append(ctx.leafText)
        }
    }

    override fun enterNode(ctx: WContext) {
        if (ctx.type == WNodeType.ANNOTATION_ENTRY) {
            entryActive = true
            entryScope = resolveScope(ctx)
            calleeSegments = mutableListOf()
            literalArgs = mutableListOf()
            return
        }
        if (!entryActive) return
        val parent = if (ctx.ancestors.isEmpty) null else ctx.ancestors.peekType()
        if (ctx.type == WNodeType.VALUE_ARGUMENT && parent == WNodeType.VALUE_ARGUMENT_LIST) {
            argChildTypes = mutableListOf()
            argIsPlainStringLiteral = false
            argLiteralText = null
            return
        }
        if (parent == WNodeType.VALUE_ARGUMENT) {
            argChildTypes.add(ctx.type)
            if (ctx.type == WNodeType.STRING_TEMPLATE) {
                templateChildTypes = mutableListOf()
                argLiteralText = StringBuilder()
                trackingTemplate = true
            }
            return
        }
        if (trackingTemplate && parent == WNodeType.STRING_TEMPLATE) {
            templateChildTypes.add(ctx.type)
        }
    }

    override fun exitNode(ctx: WContext) {
        if (!entryActive) return
        if (ctx.type == WNodeType.STRING_TEMPLATE && trackingTemplate) {
            trackingTemplate = false
            argIsPlainStringLiteral = templateChildTypes.isEmpty() ||
                templateChildTypes == listOf(WNodeType.LITERAL_STRING_TEMPLATE_ENTRY)
            return
        }
        if (ctx.type == WNodeType.VALUE_ARGUMENT) {
            if (argChildTypes == listOf(WNodeType.STRING_TEMPLATE) && argIsPlainStringLiteral) {
                literalArgs.add(argLiteralText?.toString().orEmpty())
            }
            return
        }
        if (ctx.type == WNodeType.ANNOTATION_ENTRY) {
            finalizeEntry()
            entryActive = false
        }
    }

    private fun finalizeEntry() {
        val isSuppress = calleeSegments.isNotEmpty() &&
            calleeSegments.last() == "Suppress" &&
            (calleeSegments.size == 1 || calleeSegments == listOf("kotlin", "Suppress"))
        if (!isSuppress) return
        val scope = entryScope
        for (arg in literalArgs) {
            when (scope) {
                AnnotationScope.File -> index.markFile(arg)
                is AnnotationScope.Region -> index.addRegion(arg, scope.startOffset, scope.endOffset)
                AnnotationScope.None -> {}
            }
        }
    }

    private fun resolveScope(ctx: WContext): AnnotationScope {
        val ancestors = ctx.ancestors
        if (ancestors.isEmpty) return AnnotationScope.None
        return when (ancestors.peekType()) {
            WNodeType.FILE_ANNOTATION_LIST -> AnnotationScope.File
            WNodeType.ANNOTATED_EXPRESSION ->
                AnnotationScope.Region(ancestors.peekStartOffset(), ancestors.peekEndOffset())

            WNodeType.MODIFIER_LIST -> {
                if (ancestors.size < 2) {
                    AnnotationScope.None
                } else {
                    val ownerIndex = ancestors.size - 2
                    AnnotationScope.Region(ancestors.startOffsetAt(ownerIndex), ancestors.endOffsetAt(ownerIndex))
                }
            }

            else -> AnnotationScope.None
        }
    }

    private sealed interface AnnotationScope {
        data object None : AnnotationScope
        data object File : AnnotationScope
        class Region(val startOffset: Int, val endOffset: Int) : AnnotationScope
    }
}
