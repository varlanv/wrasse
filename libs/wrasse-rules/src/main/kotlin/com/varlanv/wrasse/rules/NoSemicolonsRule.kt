package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
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
                    if (isRequiredSemicolon(ctx)) {
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
                            reportUnnecessarySemicolon(reporter, ruleId)
                        }
                    }

                    ctx.type.isWhitespaceOrComment -> {}
                    else -> {
                        pendingStart = -1
                    }
                }
            }

            private fun isRequiredSemicolon(ctx: WContext): Boolean {
                if (ctx.ancestors.isEmpty) return false
                if (ctx.ancestors.peekType() == WNodeType.CLASS_BODY) return true
                if (ctx.hasAncestor(WNodeType.FOR)) return true
                if (ctx.hasAncestor(WNodeType.ENUM_ENTRY)) return true
                return false
            }

            private fun reportUnnecessarySemicolon(reporter: WReporter, ruleId: String) {
                reporter.report(
                    ruleId, "Unnecessary semicolon",
                    pendingStart, pendingEnd, this,
                    edits = listOf(WEdit(pendingStart, pendingEnd, ""))
                )
                pendingStart = -1
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                if (pendingStart >= 0) {
                    reportUnnecessarySemicolon(reporter, ruleId)
                }
            }
        }
    }
}
