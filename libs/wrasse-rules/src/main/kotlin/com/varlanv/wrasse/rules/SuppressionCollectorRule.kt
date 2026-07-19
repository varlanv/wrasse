package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Framework-owned, always-on `WStreamRule` that rides the same single walk as every user rule,
 * collecting every `@Suppress`/`@file:Suppress`/`@kotlin.Suppress` region into [index] as it goes.
 * Not user-configurable — [com.varlanv.wrasse.model.WRuleSet.dispatchForFile]'s `alwaysOn` param
 * injects one fresh instance per file regardless of which real rules are active.
 *
 * Every annotation entry is registered in [index] before any content it can suppress is visited
 * (an ancestor's final span is known the moment it is pushed onto the stack, not just once its
 * children are visited), so [SuppressionIndex.isSuppressed] can be checked synchronously in
 * `WReporter.report` against whatever has been accumulated so far — no two-phase pre-scan, no
 * deferred re-filtering.
 *
 * **Scope resolution:** at `ANNOTATION_ENTRY` enter, the entry's immediate parent decides the
 * scope: `FILE_ANNOTATION_LIST` → file scope; `ANNOTATED_EXPRESSION` → that node's own span;
 * `MODIFIER_LIST` → the modifier list's own parent's span (the declaration/parameter/constructor it
 * modifies). Anything else — including the bracket `@[A B]` multi-annotation form — resolves no
 * scope, so such an entry is inert rather than wrongly wired.
 *
 * **Argument matching:** only a directly-written, non-interpolated string literal counts —
 * concatenation, a const reference, a named argument, an escape sequence, or string interpolation
 * is conservatively treated as non-literal and ignored. The callee name is matched syntactically:
 * simple `Suppress`, or exactly `kotlin.Suppress` — this is a syntactic check, not a resolved one,
 * so a user's own class named `Suppress` would false-match too.
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
