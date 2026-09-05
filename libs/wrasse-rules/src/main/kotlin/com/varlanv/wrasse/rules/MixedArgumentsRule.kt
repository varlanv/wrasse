package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionType
import com.varlanv.wrasse.model.WRuleOptionValue
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Reports a parenthesized argument list that mixes named and positional arguments and, when the
 * call resolved to a callee with stable parameter names outside `excluded-packages`, names the
 * positional ones (the same edits `named-arguments` would emit, so both rules on together agree).
 * A trailing lambda is outside the list and never counts. See [MixedArgumentsDecision].
 */
class MixedArgumentsRule : WUninitializedRule {
    override val id: String = "no-mixed-named-positional-arguments"
    override val canAutofix: Boolean = true
    override val requiresCallSites: Boolean = true
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = EXCLUDED_PACKAGES,
            type = WRuleOptionType.STRING_LIST,
            description = "Package prefixes whose callees are reported but never autofixed",
            default = WRuleOptionValue.StrList(listOf("java", "javax")),
        ),
    )

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        val excludedPackages = config.options.stringList(EXCLUDED_PACKAGES)
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.VALUE_ARGUMENT_LIST)

            private var sitesByCallEnd: Map<Int, WCallSite> = emptyMap()

            override fun beforeFile(ctx: WContext) {
                val usage = ctx.resolvedUsage
                if (usage == null || usage.hasResolutionErrors) return
                sitesByCallEnd = usage.callSites.associateBy { it.callEndOffset }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                var named = 0
                var positional = 0
                val written = ArrayList<WrittenArgument>(children.size)
                for (i in 0 until children.size) {
                    if (children.type(i) != WNodeType.VALUE_ARGUMENT) continue
                    val start = children.startOffset(i)
                    val end = children.endOffset(i)
                    val isNamed = MixedArgumentsDecision.isNamedArgument(ctx.sourceText, start, end)
                    if (isNamed) named++ else positional++
                    written.add(WrittenArgument(start, end, isNamed))
                }
                if (!MixedArgumentsDecision.mixesNamedAndPositional(named, positional)) return
                reporter.report(ruleId, MESSAGE, ctx.startOffset, ctx.endOffset, this, edits = fixEdits(ctx, written))
            }

            private fun fixEdits(ctx: WContext, written: List<WrittenArgument>): List<WEdit> {
                val ancestors = ctx.ancestors
                if (ancestors.isEmpty || ancestors.peekType() != WNodeType.CALL_EXPRESSION) return emptyList()
                val site = sitesByCallEnd[ancestors.peekEndOffset()] ?: return emptyList()
                if (NamedArgumentsDecision.isExcludedCallee(site, excludedPackages)) return emptyList()
                return NamedArgumentsDecision.nameEdits(site, written)
            }
        }
    }

    private companion object {
        const val EXCLUDED_PACKAGES = "excluded-packages"
        const val MESSAGE = "Named and positional arguments must not be mixed in one call"
    }
}
