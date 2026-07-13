package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

class NoSemicolonsRule : WUninitializedRule {
    override val id: String = "no-semicolons"
    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private var pendingStart = -1
            private var pendingEnd = -1

            override fun beforeFile(ctx: WContext) {
                pendingStart = -1
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.SEMICOLON) {
                    if (ctx.hasAncestor(WNodeType.FOR) || ctx.hasAncestor(WNodeType.ENUM_ENTRY)) {
                        return
                    }
                    pendingStart = ctx.startOffset
                    pendingEnd = ctx.endOffset
                    return
                }

                if (pendingStart < 0) {
                    return
                }

                when {
                    ctx.type == WNodeType.WHITE_SPACE -> {
                        if (ctx.leafText?.contains('\n') == true) {
                            reporter.report(ruleId, "Unnecessary semicolon",
                                pendingStart, pendingEnd, this)
                            pendingStart = -1
                        }
                    }
                    ctx.type.isWhitespaceOrComment -> {}
                    else -> {
                        pendingStart = -1
                    }
                }
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                if (pendingStart >= 0) {
                    reporter.report(ruleId, "Unnecessary semicolon",
                        pendingStart, pendingEnd, this)
                    pendingStart = -1
                }
            }
        }
    }
}
