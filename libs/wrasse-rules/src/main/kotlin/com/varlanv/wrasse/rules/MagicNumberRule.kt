package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * An `INTEGER_CONSTANT`/`FLOAT_CONSTANT` node (kotlinc wraps the actual literal token inside this
 * interior node, so it is visited via `enterNode`, never `visitLeaf`) whose parsed value (see
 * [NumericLiteralValue]) is not ignore-listed and whose surrounding structure exempts nothing is
 * reported (see [MagicNumberDecision]) at its own span, negated first if it is itself the operand
 * of a unary minus (its immediate parent is `PREFIX_EXPRESSION` and the immediately preceding
 * leaf is `MINUS`). `WStreamRule.enterNode` carries no reporter, so a candidate is stashed and
 * every stashed candidate is decided and reported once, together, in [afterFile].
 *
 * A plain leaf/node-rule dispatch cannot answer "is the nearest enclosing property/parameter/
 * function X" without either double-firing (a `WNodeRule` targeting a self-nestable type like
 * `PROPERTY`/`FUN` gets `onChildLeaf` invoked once per currently-open nested frame) or losing
 * cross-node facts entirely (a `WLeafRule` never sees enclosing-node context at all). A
 * [WStreamRule] with hand-rolled frame counters — the same idiom [FunctionMetricsEngine]/
 * [ThrowingExceptionInMainRule] already use — visits every node exactly once regardless of
 * nesting, so it is the only dispatch kind that is both correct and cheap here.
 */
class MagicNumberRule : WUninitializedRule {
    override val id: String = "magic-number"

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private var propertyDepth = 0
            private var parameterDefaultDepth = 0
            private val namedArgumentFrames = mutableListOf<Boolean>()
            private val funFrames = mutableListOf<FunFrame>()
            private val pendingReports = mutableListOf<PendingReport>()

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.PROPERTY -> propertyDepth++
                    WNodeType.VALUE_PARAMETER -> {
                        parameterDefaultDepth++
                        if (isOwnFunParam(ctx)) funFrames.lastOrNull()?.let { it.paramCount++ }
                    }

                    WNodeType.VALUE_ARGUMENT -> namedArgumentFrames.add(false)
                    WNodeType.FUN -> funFrames.add(FunFrame())
                    WNodeType.INTEGER_CONSTANT, WNodeType.FLOAT_CONSTANT -> checkLiteral(ctx)
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.PROPERTY -> propertyDepth--
                    WNodeType.VALUE_PARAMETER -> parameterDefaultDepth--
                    WNodeType.VALUE_ARGUMENT ->
                        if (namedArgumentFrames.isNotEmpty()) {
                            namedArgumentFrames.removeAt(namedArgumentFrames.size - 1)
                        }

                    WNodeType.FUN -> if (funFrames.isNotEmpty()) funFrames.removeAt(funFrames.size - 1)
                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.EQ ->
                        if (ctx.ancestors.peekType() == WNodeType.VALUE_ARGUMENT && namedArgumentFrames.isNotEmpty()) {
                            namedArgumentFrames[namedArgumentFrames.size - 1] = true
                        }

                    WNodeType.KW_OVERRIDE ->
                        if (isOwnFunModifier(ctx)) funFrames.lastOrNull()?.let { it.hasOverride = true }
                    WNodeType.IDENTIFIER ->
                        if (ctx.ancestors.peekType() == WNodeType.FUN) {
                            funFrames.lastOrNull()?.let { it.name = ctx.leafString() }
                        }

                    else -> {}
                }
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                for (pending in pendingReports) {
                    reporter.report(ruleId, pending.message, pending.start, pending.end, this)
                }
            }

            private fun isOwnFunModifier(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                return ancestors.peekType() ==
                    WNodeType.MODIFIER_LIST &&
                    ancestors.size >=
                    2 &&
                    ancestors.typeAt(ancestors.size - 2) ==
                    WNodeType.FUN
            }

            private fun isOwnFunParam(ctx: WContext): Boolean {
                val ancestors = ctx.ancestors
                return ancestors.peekType() ==
                    WNodeType.VALUE_PARAMETER_LIST &&
                    ancestors.size >=
                    2 &&
                    ancestors.typeAt(ancestors.size - 2) ==
                    WNodeType.FUN
            }

            private fun checkLiteral(ctx: WContext) {
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                val ancestors = ctx.ancestors
                val isNegative = ancestors.peekType() == WNodeType.PREFIX_EXPRESSION &&
                    ctx.prevLeafType == WNodeType.MINUS
                val value = NumericLiteralValue.parse(if (isNegative) "-$text" else text)

                val fun_ = funFrames.lastOrNull()
                val isHashCodeFunction = fun_ != null &&
                    fun_.name == "hashCode" &&
                    fun_.paramCount == 0 &&
                    fun_.hasOverride

                val message = MagicNumberDecision.decide(
                    value = value,
                    isInsideProperty = propertyDepth > 0,
                    isParameterDefaultValue = parameterDefaultDepth > 0,
                    isNamedArgument = namedArgumentFrames.lastOrNull() == true,
                    isHashCodeFunction = isHashCodeFunction,
                    isCallReceiver = ancestors.peekType() == WNodeType.DOT_QUALIFIED_EXPRESSION,
                    isBareFunctionReturnValue = ancestors.peekType() == WNodeType.FUN ||
                        ancestors.peekType() == WNodeType.RETURN,
                ) ?: return
                pendingReports.add(PendingReport(ctx.startOffset, ctx.endOffset, message))
            }
        }
    }

    private class FunFrame {
        var name: String? = null
        var paramCount: Int = 0
        var hasOverride: Boolean = false
    }

    private class PendingReport(
        val start: Int,
        val end: Int,
        val message: String,
    )
}
