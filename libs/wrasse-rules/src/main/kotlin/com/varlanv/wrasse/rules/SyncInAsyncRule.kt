package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.ChildLeafHandler
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(WNodeType.CALL_EXPRESSION, WNodeType.FUN, WNodeType.REFERENCE_EXPRESSION)

/**
 * A `runBlocking { }` call (a trailing-lambda `CALL_EXPRESSION` whose own callee is the bare name
 * `runBlocking`) reached from inside an `async { }`/`launch { }` call or a `suspend` function
 * anywhere in its enclosing scope is reported (see [SyncInAsyncDecision]) at the callee's own span.
 * A stack of open `CALL_EXPRESSION` frames tracks each one's own callee name (read directly off
 * its callee `REFERENCE_EXPRESSION`'s own span text — no resolution of what `async`/`launch`
 * actually name); a parallel stack of open `FUN` frames tracks each one's own `suspend` modifier.
 * Whether *any* currently open frame (of either kind) matches is a pure existence check, so the two
 * stacks never need to be merged into one ordered structure.
 */
class SyncInAsyncRule : WUninitializedRule {
    override val id: String = "sync-in-async"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule, ChildLeafHandler {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val callFrames = mutableListOf<CallFrame>()
            private val funSuspendFrames = mutableListOf<Boolean>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CALL_EXPRESSION -> callFrames.add(CallFrame())
                    WNodeType.FUN -> funSuspendFrames.add(false)
                    WNodeType.REFERENCE_EXPRESSION -> handleCalleeReference(ctx)
                    else -> {}
                }
                return true
            }

            private fun handleCalleeReference(ctx: WContext) {
                if (ctx.ancestors.peekType() != WNodeType.CALL_EXPRESSION || ctx.childIndex != 0) return
                val frame = callFrames.lastOrNull() ?: return
                val text = ctx.sourceText.subSequence(ctx.startOffset, ctx.endOffset)
                when {
                    text.contentEquals("async") || text.contentEquals("launch") -> frame.isAsyncOrLaunch = true
                    text.contentEquals("runBlocking") -> {
                        frame.runBlockingStart = ctx.startOffset
                        frame.runBlockingEnd = ctx.endOffset
                    }

                    else -> {}
                }
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type != WNodeType.KW_SUSPEND) return
                val ancestors = ctx.ancestors
                if (ancestors.peekType() != WNodeType.MODIFIER_LIST || ancestors.size < 2) return
                if (ancestors.typeAt(ancestors.size - 2) != WNodeType.FUN) return
                if (funSuspendFrames.isNotEmpty()) funSuspendFrames[funSuspendFrames.size - 1] = true
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.FUN ->
                        if (funSuspendFrames.isNotEmpty()) funSuspendFrames.removeAt(funSuspendFrames.size - 1)
                    WNodeType.CALL_EXPRESSION -> finalizeCall(children, reporter)
                    else -> {}
                }
            }

            private fun finalizeCall(children: ChildBuffer, reporter: WReporter) {
                val frame = if (callFrames.isNotEmpty()) callFrames.removeAt(callFrames.size - 1) else return
                if (frame.runBlockingStart < 0) return
                if (!children.hasChildOfType(WNodeType.LAMBDA_ARGUMENT)) return

                val hasGoverningContext = callFrames.any { it.isAsyncOrLaunch } || funSuspendFrames.any { it }
                val message = SyncInAsyncDecision.decide(
                    isRunBlockingTrailingLambdaCall = true,
                    hasGoverningAsyncContext = hasGoverningContext,
                ) ?: return
                reporter.report(ruleId, message, frame.runBlockingStart, frame.runBlockingEnd, this)
            }
        }
    }

    private class CallFrame(
        var isAsyncOrLaunch: Boolean = false,
        var runBlockingStart: Int = -1,
        var runBlockingEnd: Int = -1,
    )
}
