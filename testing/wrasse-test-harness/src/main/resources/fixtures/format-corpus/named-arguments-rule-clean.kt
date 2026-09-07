package sample

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

private val TARGET_TYPES = setOf(WNodeType.VALUE_ARGUMENT_LIST)

/**
 * Names the positional arguments of a call whose arguments include another call with arguments
 * (the whole nested tree of such calls), or of every call with `all-calls: true`. A callee that
 * declares fewer than `threshold` (default 2) parameters, a trailing lambda's not counted, goes the other way: its arguments are
 * written positionally, so a call of one loses its names when positional form means the same
 * call ([NamedArgumentsDecision.positionalEdits]). A call mixing named and positional arguments
 * is settled in the same pass whatever its nesting — made positional when the callee is below
 * the threshold and that is possible, fully named otherwise — unless `allow-mixed` is true, in
 * which case a mixed call is left exactly as written. The `wrap`
 * option (default true) is read by the printer, not here: with format enabled it lays those calls
 * out one argument per line ([com.varlanv.wrasse.model.FormatStyle.wrapNestedCallArguments]). A callee
 * in an `excluded-packages` package (`java` and `javax` by default), one without stable parameter
 * names, and a function type's `invoke` are called positionally whatever the threshold: never
 * named, and names already written are dropped when positional form means the same call. A vararg
 * element and a trailing lambda are never touched. A call whose callee has [WCallSite.namingIsAmbiguous]
 * set is never named — some other visible overload could also accept the fully named call; a call
 * whose callee has [WCallSite.positionalIsAmbiguous] set never has names dropped — some other
 * visible overload could also accept the same call written positionally — each flag leaving the
 * other direction unaffected. A call whose syntactic reading of which arguments are named
 * disagrees with what FIR itself resolved ([NamedArgumentsDecision.hasSyntaxMismatch]) is left
 * untouched entirely. Inert for a file whose resolution has errors. See [NamedArgumentsDecision].
 */
class NamedArgumentsRule : WUninitializedRule {
    override val id: String = "named-arguments"
    override val canAutofix: Boolean = true
    override val requiresCallSites: Boolean = true
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Optional(
            name = EXCLUDED_PACKAGES,
            type = WRuleOptionType.STRING_LIST,
            description = "Package prefixes whose callees are called positionally: never named, and named arguments dropped when the order allows",
            default = WRuleOptionValue.StrList(listOf("java", "javax")),
        ),
        WRuleOptionSpec.Optional(
            name = ALL_CALLS,
            type = WRuleOptionType.BOOLEAN,
            description = "Name the positional arguments of every call, not only of calls nesting other calls",
            default = WRuleOptionValue.Bool(false),
        ),
        WRuleOptionSpec.Optional(
            name = THRESHOLD,
            type = WRuleOptionType.INTEGER,
            description = "Name the arguments of callees declaring at least this many parameters; write those of narrower callees positionally",
            default = WRuleOptionValue.Num(2),
            minimum = 1,
            maximum = Int.MAX_VALUE.toLong(),
        ),
        WRuleOptionSpec.Optional(
            name = ALLOW_MIXED,
            type = WRuleOptionType.BOOLEAN,
            description = "Leave a call that mixes named and positional arguments exactly as written",
            default = WRuleOptionValue.Bool(false),
        ),
        WRuleOptionSpec.Optional(
            name = WRAP,
            type = WRuleOptionType.BOOLEAN,
            description = "With format enabled, also lay out every call nesting another call with arguments one argument per line",
            default = WRuleOptionValue.Bool(true),
        ),
    )

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        val excludedPackages = config.options.stringList(EXCLUDED_PACKAGES)
        val allCalls = config.options.boolean(ALL_CALLS)
        val threshold = config.options.integer(THRESHOLD).toInt()
        val allowMixed = config.options.boolean(ALLOW_MIXED)
        val positionalMessage = "Arguments of a callee with fewer than $threshold parameters should be positional"
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private var sitesByCallEnd: Map<Int, WCallSite> = emptyMap()
            private var inScope: Set<Int> = emptySet()

            override fun beforeFile(ctx: WContext) {
                val usage = ctx.resolvedUsage
                if (usage == null || usage.hasResolutionErrors) return
                sitesByCallEnd = usage.callSites.associateBy { it.callEndOffset }
                inScope = NamedArgumentsDecision.callsInScope(usage.callSites, allCalls, threshold)
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val ancestors = ctx.ancestors
                if (ancestors.isEmpty || ancestors.typeAt(ancestors.size - 1) != WNodeType.CALL_EXPRESSION) return
                val callEnd = ancestors.peekEndOffset()
                val site = sitesByCallEnd[callEnd] ?: return
                val excluded = NamedArgumentsDecision.isExcludedCallee(site, excludedPackages)
                val written = ArrayList<WrittenArgument>(children.size)
                for (i in 0 until children.size) {
                    if (children.type(i) != WNodeType.VALUE_ARGUMENT) continue
                    val start = children.startOffset(i)
                    val end = children.endOffset(i)
                    written.add(
                        WrittenArgument(
                            start,
                            end,
                            NamedArgumentsDecision.namedArgumentValueStart(ctx.sourceText, start, end),
                        ),
                    )
                }
                if (NamedArgumentsDecision.hasSyntaxMismatch(site, written)) return
                val mixed = NamedArgumentsDecision.isMixed(site, written)
                if (mixed && allowMixed) return
                if (excluded || NamedArgumentsDecision.parenthesizedParameterCount(site) < threshold) {
                    if (!site.positionalIsAmbiguous) {
                        val edits = NamedArgumentsDecision.positionalEdits(site, written)
                        if (edits.isNotEmpty()) {
                            val message = if (excluded) EXCLUDED_MESSAGE else positionalMessage
                            reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this, edits = edits)
                            return
                        }
                    }
                    if (excluded || !mixed) return
                } else if (!mixed && callEnd !in inScope) {
                    return
                }
                if (site.namingIsAmbiguous) return
                val edits = NamedArgumentsDecision.nameEdits(site, written)
                if (edits.isEmpty()) return
                reporter.report(ruleId, MESSAGE, ctx.startOffset, ctx.endOffset, this, edits = edits)
            }
        }
    }

    private companion object {
        const val EXCLUDED_PACKAGES = "excluded-packages"
        const val ALL_CALLS = "all-calls"
        const val THRESHOLD = "threshold"
        const val ALLOW_MIXED = "allow-mixed"
        const val WRAP = "wrap"
        const val MESSAGE = "Positional arguments should be named"
        const val EXCLUDED_MESSAGE = "Arguments of a callee in an excluded package should be positional"
    }
}

// expect-clean
// fixture-option: trailing-newline
