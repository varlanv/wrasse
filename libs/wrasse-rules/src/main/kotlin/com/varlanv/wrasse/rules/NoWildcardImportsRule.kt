package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeRule
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

class NoWildcardImportsRule : WUninitializedRule {
    override val id: String = "no-wildcard-imports"

    override fun initRule(config: WrasseRuleConfig): WNodeRule {
        val ruleId = id
        return object : WNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes: Set<WNodeType> = setOf(WNodeType.IMPORT_DIRECTIVE)

            private var importStart = -1
            private var importEnd = -1
            private var sawMul = false

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                importStart = ctx.startOffset
                importEnd = ctx.endOffset
                sawMul = false
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.MUL) {
                    sawMul = true
                }
            }

            override fun exitNode(ctx: WContext, reporter: WReporter) {
                if (sawMul) {
                    reporter.report(ruleId, "Replace wildcard import with explicit imports",
                        importStart, importEnd, this)
                }
            }
        }
    }
}
