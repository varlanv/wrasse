package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRuleOptionSpec
import com.varlanv.wrasse.model.WRuleOptionType
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.CALL_EXPRESSION)

/**
 * Reports every call, written with call syntax, whose resolved callee matches one of the
 * `calls` option's patterns (see [ForbiddenCallsDecision] for the pattern forms), unless the
 * current file matches one of the globs listed for that pattern — the globs are relative to the
 * directory of `wrasse.json`, like `exclude`. Reported at the call expression itself, receiver
 * excluded. Report-only.
 */
class ForbiddenCallsRule : WUninitializedRule {
    override val id: String = "forbidden-calls"
    override val requiresCallSites: Boolean = true
    override val options: List<WRuleOptionSpec> = listOf(
        WRuleOptionSpec.Required(
            name = CALLS,
            type = WRuleOptionType.STRING_LIST_MAP,
            description = "Forbidden callee names (pkg.Class.member, pkg.member, pkg.Class for constructors, trailing * for a prefix), each mapped to the file globs where it stays allowed",
        ),
    )

    @Volatile
    private var compiled: Pair<WrasseRuleConfig, List<ForbiddenCallsDecision.ForbiddenCall>>? = null

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        val forbidden = compiledFor(config)
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private var messagesByCallEnd: Map<Int, String> = emptyMap()

            override fun beforeFile(ctx: WContext) {
                val sites = ctx.resolvedUsage?.callSites ?: return
                if (sites.isEmpty()) return
                val active = forbidden.filter { !it.allowedIn(ctx.configRelativeFilePath) }
                if (active.isEmpty()) return
                val messages = HashMap<Int, String>()
                for (site in sites) {
                    val canonical = ForbiddenCallsDecision.canonicalName(site)
                    if (active.any { it.matches(canonical) }) {
                        messages[site.callEndOffset] = ForbiddenCallsDecision.message(canonical)
                    }
                }
                messagesByCallEnd = messages
            }

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                val message = messagesByCallEnd[ctx.endOffset]
                if (message != null) reporter.report(ruleId, message, ctx.startOffset, ctx.endOffset, this)
                return false
            }
        }
    }

    private fun compiledFor(config: WrasseRuleConfig): List<ForbiddenCallsDecision.ForbiddenCall> {
        val cached = compiled
        if (cached != null && cached.first === config) return cached.second
        val fresh = ForbiddenCallsDecision.compile(config.options.stringListMap(CALLS))
        compiled = config to fresh
        return fresh
    }

    private companion object {
        const val CALLS = "calls"
    }
}
