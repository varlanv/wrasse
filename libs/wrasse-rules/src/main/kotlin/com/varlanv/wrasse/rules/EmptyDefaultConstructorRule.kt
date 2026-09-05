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
 * A class's own empty, unmodified, uncalled primary constructor (`class Foo()`) has its parameter
 * list removed entirely; see [EmptyDefaultConstructorDecision] for the exact bail conditions.
 */
class EmptyDefaultConstructorRule : WUninitializedRule {
    override val id: String = "empty-default-constructor"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(
                WNodeType.CLASS,
                WNodeType.PRIMARY_CONSTRUCTOR,
                WNodeType.MODIFIER_LIST,
                WNodeType.VALUE_PARAMETER_LIST,
                WNodeType.CONSTRUCTOR_DELEGATION_CALL,
            )

            private val classes = mutableListOf<PendingClass>()
            private val constructors = mutableListOf<PendingConstructor>()
            private val delegations = mutableListOf<DelegationFrame>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CLASS -> classes.add(PendingClass())
                    WNodeType.PRIMARY_CONSTRUCTOR -> constructors.add(PendingConstructor())
                    WNodeType.CONSTRUCTOR_DELEGATION_CALL -> delegations.add(DelegationFrame())
                    else -> {}
                }
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                val delegation = delegations.lastOrNull() ?: return
                val insideArgs = ctx.hasAncestor(WNodeType.VALUE_ARGUMENT_LIST)
                if (!insideArgs && ctx.type == WNodeType.KW_THIS) {
                    delegation.sawThis = true
                } else if (insideArgs &&
                    ctx.type != WNodeType.LPAR &&
                    ctx.type != WNodeType.RPAR &&
                    !ctx.type.isWhitespaceOrComment
                ) {
                    delegation.hasArg = true
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.MODIFIER_LIST -> recordModifierList(ctx, children)
                    WNodeType.VALUE_PARAMETER_LIST -> recordValueParameterList(ctx, children)
                    WNodeType.PRIMARY_CONSTRUCTOR -> recordConstructor(ctx, children)
                    WNodeType.CONSTRUCTOR_DELEGATION_CALL -> recordDelegationCall()
                    WNodeType.CLASS -> finalizeClass(reporter)
                    else -> {}
                }
            }

            private fun recordModifierList(ctx: WContext, children: ChildBuffer) {
                when (ctx.ancestors.peekType()) {
                    WNodeType.CLASS -> {
                        val cls = classes.lastOrNull() ?: return
                        if (children.hasChildOfType(
                            WNodeType.KW_EXPECT,
                        ) || children.hasChildOfType(WNodeType.KW_ACTUAL)) {
                            cls.isExpectOrActual = true
                        }
                    }
                    WNodeType.PRIMARY_CONSTRUCTOR -> {
                        val ctor = constructors.lastOrNull() ?: return
                        ctor.hasAnnotation = children.hasChildOfType(WNodeType.ANNOTATION_ENTRY)
                        for (i in 0 until children.size) {
                            val type = children.type(i)
                            if (type ==
                                WNodeType.KW_PUBLIC ||
                                type ==
                                WNodeType.KW_PRIVATE ||
                                type ==
                                WNodeType.KW_PROTECTED ||
                                type ==
                                WNodeType.KW_INTERNAL
                            ) {
                                ctor.visibility = type
                            }
                        }
                    }
                    else -> {}
                }
            }

            private fun recordValueParameterList(ctx: WContext, children: ChildBuffer) {
                if (ctx.ancestors.peekType() != WNodeType.PRIMARY_CONSTRUCTOR) return
                val ctor = constructors.lastOrNull() ?: return
                ctor.hasValueParameter = children.hasChildOfType(WNodeType.VALUE_PARAMETER)
                val lparIdx = children.firstChildOfType(WNodeType.LPAR)
                val rparIdx = children.firstChildOfType(WNodeType.RPAR)
                if (lparIdx < 0 || rparIdx < 0) return
                ctor.vpStart = children.startOffset(lparIdx)
                ctor.vpEnd = children.endOffset(rparIdx)
                for (i in (lparIdx + 1) until rparIdx) {
                    val type = children.type(i)
                    if (type.isWhitespaceOrComment && type != WNodeType.WHITE_SPACE) {
                        ctor.hasCommentInParens = true
                    }
                }
            }

            private fun recordConstructor(ctx: WContext, children: ChildBuffer) {
                val ctor = constructors.removeAt(constructors.size - 1)
                ctor.hasKeyword = children.hasChildOfType(WNodeType.KW_CONSTRUCTOR)
                ctor.reportStart = ctx.startOffset
                ctor.reportEnd = ctx.endOffset
                val cls = classes.lastOrNull() ?: return
                cls.constructor = ctor
            }

            private fun recordDelegationCall() {
                val delegation = delegations.removeAt(delegations.size - 1)
                if (delegation.sawThis && !delegation.hasArg) {
                    classes.lastOrNull()?.calledWithEmptyThis = true
                }
            }

            private fun finalizeClass(reporter: WReporter) {
                val cls = classes.removeAt(classes.size - 1)
                val ctor = cls.constructor ?: return
                if (ctor.vpStart < 0) return
                val verdict =
                    EmptyDefaultConstructorDecision.decide(
                        hasValueParameter = ctor.hasValueParameter,
                        hasAnnotation = ctor.hasAnnotation,
                        visibility = ctor.visibility,
                        isExpectOrActual = cls.isExpectOrActual,
                        calledWithEmptyThis = cls.calledWithEmptyThis,
                        hasKeyword = ctor.hasKeyword,
                        hasCommentInParens = ctor.hasCommentInParens,
                        vpStart = ctor.vpStart,
                        vpEnd = ctor.vpEnd,
                    ) ?: return
                reporter.report(
                    ruleId,
                    EmptyDefaultConstructorDecision.MESSAGE,
                    ctor.reportStart,
                    ctor.reportEnd,
                    this,
                    edits = verdict.edits,
                )
            }
        }
    }

    private class PendingClass {
        var isExpectOrActual = false
        var calledWithEmptyThis = false
        var constructor: PendingConstructor? = null
    }

    private class PendingConstructor {
        var hasAnnotation = false
        var visibility: WNodeType? = null
        var hasValueParameter = false
        var hasCommentInParens = false
        var hasKeyword = false
        var vpStart = -1
        var vpEnd = -1
        var reportStart = 0
        var reportEnd = 0
    }

    private class DelegationFrame {
        var sawThis = false
        var hasArg = false
    }
}
