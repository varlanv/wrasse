package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFileRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRuleGroup
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * Fuses two class-scoped metric ids into one decision-maker: `too-many-functions`,
 * `large-class`. A single [WStreamRule] walk maintains one stack of [ClassMetricsFrame]s (pushed
 * on `CLASS`/`OBJECT_DECLARATION` enter, popped on exit) plus a plain file-level top-level
 * function counter for `too-many-functions`' file scope. A nested class/object's own frame never
 * contributes to an enclosing one, mirroring [FunctionMetricsEngine]'s policy.
 */
class ClassMetricsEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(TOO_MANY_FUNCTIONS_ID, LARGE_CLASS_ID)

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val tooManyFunctionsRule = configs[TOO_MANY_FUNCTIONS_ID]?.let { ReportFacade(TOO_MANY_FUNCTIONS_ID, it) }
        val largeClassRule = configs[LARGE_CLASS_ID]?.let { ReportFacade(LARGE_CLASS_ID, it) }

        return object : WStreamRule {
            override val id = ENGINE_ID
            override val config = configs.values.first()

            private val frames = mutableListOf<ClassMetricsFrame>()
            private val completed = mutableListOf<ClassMetricsFrame>()
            private var topLevelFunctionCount = 0
            private var currentLine = 0

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.CLASS -> frames.add(ClassMetricsFrame(-1, -1, "", "Class"))
                    WNodeType.OBJECT_DECLARATION -> frames.add(ClassMetricsFrame(-1, -1, "", "Object"))
                    WNodeType.FUN ->
                        when (ctx.ancestors.peekType()) {
                            WNodeType.CLASS_BODY -> frames.lastOrNull()?.recordFunction()
                            WNodeType.FILE -> topLevelFunctionCount++
                            else -> {}
                        }

                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.CLASS, WNodeType.OBJECT_DECLARATION ->
                        if (frames.isNotEmpty()) {
                            completed.add(frames.removeAt(frames.size - 1))
                        }
                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafText
                if (ctx.type.isWhitespaceOrComment) {
                    if (text != null) for (i in 0 until text.length) if (text[i] == '\n') currentLine++
                    return
                }

                frames.lastOrNull()?.recordCodeLine(currentLine)

                when (ctx.type) {
                    WNodeType.KW_INTERFACE -> frames
                        .lastOrNull()
                        ?.let { if (it.kindLabel == "Class") it.kindLabel = "Interface" }
                    WNodeType.KW_ENUM -> frames
                        .lastOrNull()
                        ?.let { if (it.kindLabel == "Class") it.kindLabel = "Enum class" }
                    WNodeType.IDENTIFIER -> {
                        val frame = frames.lastOrNull()
                        val parent = ctx.ancestors.peekType()
                        if (frame != null &&
                            frame.nameStart < 0 &&
                            (parent == WNodeType.CLASS || parent == WNodeType.OBJECT_DECLARATION)) {
                            frame.nameStart = ctx.startOffset
                            frame.nameEnd = ctx.endOffset
                            frame.declarationName = IdentifierCasing.unquote(text ?: "")
                        }
                    }

                    else -> {}
                }

                if (text != null) for (i in 0 until text.length) if (text[i] == '\n') currentLine++
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                if (tooManyFunctionsRule != null) {
                    TooManyFunctionsDecision.decideFile(topLevelFunctionCount)?.let {
                        reporter.report(TOO_MANY_FUNCTIONS_ID, it, 0, 0, tooManyFunctionsRule)
                    }
                }
                for (frame in completed) {
                    val name = frame.declarationName.ifEmpty { "<anonymous>" }
                    val start = if (frame.nameStart >= 0) frame.nameStart else 0
                    val end = if (frame.nameStart >= 0) frame.nameEnd else 0

                    if (tooManyFunctionsRule != null) {
                        TooManyFunctionsDecision.decide(frame.functionCount, frame.kindLabel, name)?.let {
                            reporter.report(TOO_MANY_FUNCTIONS_ID, it, start, end, tooManyFunctionsRule)
                        }
                    }
                    if (largeClassRule != null) {
                        LargeClassDecision.decide(frame.distinctCodeLines, name)?.let {
                            reporter.report(LARGE_CLASS_ID, it, start, end, largeClassRule)
                        }
                    }
                }
            }
        }
    }

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    private companion object {
        const val ENGINE_ID = "class-metrics-engine"
        const val TOO_MANY_FUNCTIONS_ID = "too-many-functions"
        const val LARGE_CLASS_ID = "large-class"
    }
}
