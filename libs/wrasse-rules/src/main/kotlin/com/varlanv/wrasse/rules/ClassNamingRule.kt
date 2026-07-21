package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * `class`/`interface`/`object` names must be PascalCase (report-only, see [ClassNamingDecision]).
 *
 * A file that imports `org.junit.jupiter.api` (JUnit 5) is treated as test code: no
 * test-source-set concept exists in wrasse's streaming model, so an import-based heuristic is the
 * only reachable signal. Under that heuristic, a backtick-wrapped class name of any non-empty
 * content is allowed.
 */
class ClassNamingRule : WUninitializedRule {
    override val id: String = "class-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IMPORT_DIRECTIVE, WNodeType.CLASS, WNodeType.OBJECT_DECLARATION)

            private var isJUnitJupiterImported = false

            override fun exitNode(ctx: WContext, children: ChildBuffer, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> {
                        if (!isJUnitJupiterImported &&
                            TestImportHeuristic.matches(ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset), JUNIT_JUPITER)
                        ) {
                            isJUnitJupiterImported = true
                        }
                    }

                    WNodeType.CLASS, WNodeType.OBJECT_DECLARATION -> {
                        val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                        if (idIdx < 0) return
                        val identifierText = children.textSpan(idIdx, ctx.sourceText)
                        val message = ClassNamingDecision.decide(identifierText, isJUnitJupiterImported) ?: return
                        reporter.report(ruleId, message, children.startOffset(idIdx), children.endOffset(idIdx), this)
                    }

                    else -> {}
                }
            }
        }
    }

    private companion object {
        val JUNIT_JUPITER = setOf("org.junit.jupiter.api")
    }
}
