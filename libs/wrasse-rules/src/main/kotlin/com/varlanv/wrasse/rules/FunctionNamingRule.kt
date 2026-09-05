package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * Function names must be lowerCamelCase (report-only, see [FunctionNamingDecision]).
 *
 * A factory function — one whose declared return type, or whose single-expression body is an
 * unqualified call, names the same identifier as the function itself — is exempt. An anonymous
 * function (no name identifier) is never a target at all. An override is always exempt, since its
 * name may be fixed by a supertype outside this project.
 */
class FunctionNamingRule : WUninitializedRule {
    override val id: String = "function-naming"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.IMPORT_DIRECTIVE, WNodeType.FUN)

            private var isTestLibraryImported = false

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> {
                        if (!isTestLibraryImported &&
                            TestImportHeuristic.matches(
                                ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset),
                                TEST_LIBRARIES,
                            )
                        ) {
                            isTestLibraryImported = true
                        }
                    }

                    WNodeType.FUN -> visitFun(ctx, children, reporter)
                    else -> {}
                }
            }

            private fun visitFun(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                val identifierText = children.textSpan(idIdx, ctx.sourceText)
                val name = IdentifierCasing.unquote(identifierText)
                val isOverride = hasOverrideModifier(ctx, children)
                val isFactory = isFactoryFunction(ctx, children, name)
                val message = FunctionNamingDecision.decide(
                    identifierText,
                    isOverride,
                    isFactory,
                    isTestLibraryImported,
                ) ?: return
                reporter.report(ruleId, message, children.startOffset(idIdx), children.endOffset(idIdx), this)
            }

            private fun hasOverrideModifier(ctx: WContext, children: ChildBuffer): Boolean {
                val idx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                return idx >= 0 && Regex("\\boverride\\b").containsMatchIn(children.textSpan(idx, ctx.sourceText))
            }

            private fun isFactoryFunction(
                ctx: WContext,
                children: ChildBuffer,
                name: String,
            ): Boolean {
                val colonIdx = children.firstChildOfType(WNodeType.COLON)
                if (colonIdx >= 0) {
                    val typeIdx = nextNonTrivia(children, colonIdx)
                    if (typeIdx >= 0 && children.type(typeIdx) == WNodeType.TYPE_REFERENCE) {
                        val declaredReturnType = children
                            .textSpan(typeIdx, ctx.sourceText)
                            .toString()
                            .substringBefore('<')
                        return declaredReturnType == name
                    }
                    return false
                }
                val eqIdx = children.firstChildOfType(WNodeType.EQ)
                if (eqIdx >= 0) {
                    val bodyIdx = nextNonTrivia(children, eqIdx)
                    if (bodyIdx >= 0 && children.type(bodyIdx) == WNodeType.CALL_EXPRESSION) {
                        val callText = children.textSpan(bodyIdx, ctx.sourceText).toString().substringBefore('(')
                        return callText == name
                    }
                }
                return false
            }

            private fun nextNonTrivia(children: ChildBuffer, fromIndexExclusive: Int): Int {
                var i = fromIndexExclusive + 1
                while (i < children.size && children.type(i).isWhitespaceOrComment) i++
                return if (i < children.size) i else -1
            }
        }
    }

    private companion object {
        val TEST_LIBRARIES = setOf("io.kotest", "junit.framework", "kotlin.test", "org.junit", "org.testng")
    }
}
