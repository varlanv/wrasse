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
 * Fuses five function-scoped metric ids into one decision-maker: `return-count`, `throws-count`,
 * `nested-block-depth`, `cyclomatic-complexity`, `long-method`. A single [WStreamRule] walk
 * maintains one stack of [FunctionMetricsFrame]s (pushed on `FUN` enter, popped on exit) so every
 * metric is accumulated in the same pass instead of five independent walks each re-deriving the
 * same function-nesting boundary. A nested function's own frame is completely independent — none
 * of its counters ever merge into an enclosing frame — so every metric answers "did this
 * particular declaration, on its own, cross the line." Lambdas and object literals are
 * transparent: they push no frame of their own, so their contents attribute to the nearest
 * enclosing `FUN`, exactly like any other control-flow construct.
 */
class FunctionMetricsEngine : WUninitializedRuleGroup {
    override val ids: Set<String> = setOf(RETURN_COUNT_ID, THROWS_COUNT_ID, NESTED_BLOCK_DEPTH_ID, CYCLOMATIC_COMPLEXITY_ID, LONG_METHOD_ID)

    override fun initGroup(configs: Map<String, WrasseRuleConfig>): WRule {
        val returnCountRule = configs[RETURN_COUNT_ID]?.let { ReportFacade(RETURN_COUNT_ID, it) }
        val throwsCountRule = configs[THROWS_COUNT_ID]?.let { ReportFacade(THROWS_COUNT_ID, it) }
        val nestedBlockDepthRule = configs[NESTED_BLOCK_DEPTH_ID]?.let { ReportFacade(NESTED_BLOCK_DEPTH_ID, it) }
        val cyclomaticComplexityRule = configs[CYCLOMATIC_COMPLEXITY_ID]?.let { ReportFacade(CYCLOMATIC_COMPLEXITY_ID, it) }
        val longMethodRule = configs[LONG_METHOD_ID]?.let { ReportFacade(LONG_METHOD_ID, it) }

        return object : WStreamRule {
            override val id = ENGINE_ID
            override val config = configs.values.first()

            private val frames = mutableListOf<PendingFrame>()
            private val completed = mutableListOf<PendingFrame>()
            private var currentLine = 0

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.FUN -> frames.add(PendingFrame(ctx.startOffset, ctx.endOffset))
                    WNodeType.RETURN -> frames.lastOrNull()?.frame?.recordReturn()
                    WNodeType.THROW -> frames.lastOrNull()?.frame?.recordThrow()
                    WNodeType.CONTINUE, WNodeType.BREAK, WNodeType.CATCH, WNodeType.WHEN_ENTRY ->
                    frames.lastOrNull()?.frame?.addComplexity(1)

                    WNodeType.IF -> {
                        frames.lastOrNull()?.frame?.addComplexity(1)
                        if (ctx.ancestors.peekType() != WNodeType.ELSE) frames.lastOrNull()?.frame?.enterNestingConstruct()
                    }

                    WNodeType.WHEN, WNodeType.TRY -> frames.lastOrNull()?.frame?.enterNestingConstruct()

                    WNodeType.FOR, WNodeType.WHILE, WNodeType.DO_WHILE -> {
                        frames.lastOrNull()?.frame?.addComplexity(1)
                        frames.lastOrNull()?.frame?.enterNestingConstruct()
                    }

                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IF -> if (ctx.ancestors.peekType() != WNodeType.ELSE) frames.lastOrNull()?.frame?.exitNestingConstruct()
                    WNodeType.WHEN, WNodeType.TRY, WNodeType.FOR, WNodeType.WHILE, WNodeType.DO_WHILE ->
                    frames.lastOrNull()?.frame?.exitNestingConstruct()

                    WNodeType.FUN -> if (frames.isNotEmpty()) completed.add(frames.removeAt(frames.size - 1))
                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                val text = ctx.leafText
                if (ctx.type.isWhitespaceOrComment) {
                    if (text != null) for (i in 0 until text.length) if (text[i] == '\n') currentLine++
                    return
                }

                frames.lastOrNull()?.frame?.recordCodeLine(currentLine)

                when (ctx.type) {
                    WNodeType.ANDAND, WNodeType.OROR, WNodeType.ELVIS -> frames.lastOrNull()?.frame?.addComplexity(1)
                    WNodeType.IDENTIFIER -> {
                        val pending = frames.lastOrNull()
                        if (pending != null && ctx.ancestors.peekType() == WNodeType.FUN && pending.frame.nameStart < 0) {
                            pending.frame.nameStart = ctx.startOffset
                            pending.frame.nameEnd = ctx.endOffset
                            pending.frame.functionName = IdentifierCasing.unquote(text ?: "")
                        }
                    }

                    else -> {}
                }

                if (text != null) for (i in 0 until text.length) if (text[i] == '\n') currentLine++
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (pending in completed) {
                    val f = pending.frame
                    val name = f.functionName.ifEmpty { "<anonymous>" }
                    val reportStart = if (f.nameStart >= 0) f.nameStart else pending.funStart
                    val reportEnd = if (f.nameStart >= 0) f.nameEnd else pending.funEnd

                    if (returnCountRule != null) {
                        ReturnCountDecision.decide(f.returnCount, name)?.let {
                            reporter.report(RETURN_COUNT_ID, it, reportStart, reportEnd, returnCountRule)
                        }
                    }
                    if (throwsCountRule != null) {
                        ThrowsCountDecision.decide(f.throwCount, name)?.let {
                            reporter.report(THROWS_COUNT_ID, it, reportStart, reportEnd, throwsCountRule)
                        }
                    }
                    if (nestedBlockDepthRule != null) {
                        NestedBlockDepthDecision.decide(f.maxNestingDepth, name)?.let {
                            reporter.report(NESTED_BLOCK_DEPTH_ID, it, reportStart, reportEnd, nestedBlockDepthRule)
                        }
                    }
                    if (cyclomaticComplexityRule != null) {
                        CyclomaticComplexityDecision.decide(f.complexity, name)?.let {
                            reporter.report(CYCLOMATIC_COMPLEXITY_ID, it, reportStart, reportEnd, cyclomaticComplexityRule)
                        }
                    }
                    if (longMethodRule != null) {
                        LongMethodDecision.decide(f.distinctCodeLines, name)?.let {
                            reporter.report(LONG_METHOD_ID, it, reportStart, reportEnd, longMethodRule)
                        }
                    }
                }
            }
        }
    }

    private class PendingFrame(val funStart: Int, val funEnd: Int) {
        val frame = FunctionMetricsFrame(-1, -1, "")
    }

    private class ReportFacade(override val id: String, override val config: WrasseRuleConfig) : WFileRule {
        override fun visit(ctx: WContext, reporter: WReporter) {}
    }

    private companion object {
        const val ENGINE_ID = "function-metrics-engine"
        const val RETURN_COUNT_ID = "return-count"
        const val THROWS_COUNT_ID = "throws-count"
        const val NESTED_BLOCK_DEPTH_ID = "nested-block-depth"
        const val CYCLOMATIC_COMPLEXITY_ID = "cyclomatic-complexity"
        const val LONG_METHOD_ID = "long-method"
    }
}
