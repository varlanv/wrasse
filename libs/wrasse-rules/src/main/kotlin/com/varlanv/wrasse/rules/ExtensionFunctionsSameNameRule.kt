package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

/**
 * Collects, across the whole file: every non-interface class's own related supertypes (see
 * [ExtensionFunctionsSameNameDecision]'s note on file-only scope), and every top-level extension
 * function's own receiver class, name, parameter names, and return type text. Decided once in
 * [afterFile], reporting both functions of any pair found related. A class's own name is captured
 * from its first direct `IDENTIFIER` child (before its own `SUPER_TYPE_LIST`, if any, is walked); a
 * function's own parameter names are captured the same way from its `VALUE_PARAMETER_LIST`'s own
 * `VALUE_PARAMETER` children, stashed by that list's own offset for its enclosing `FUN` to collect
 * at its own exit.
 */
class ExtensionFunctionsSameNameRule : WUninitializedRule {
    override val id: String = "extension-functions-same-name"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(
                WNodeType.CLASS,
                WNodeType.SUPER_TYPE_LIST,
                WNodeType.FUN,
                WNodeType.VALUE_PARAMETER_LIST,
            )

            private val classFrames = mutableListOf<ClassFrame>()
            private val paramListFrames = mutableListOf<MutableList<String>>()
            private val paramNamesByListStart = mutableMapOf<Int, List<String>>()
            private val relatedClassPairs = mutableListOf<Pair<String, String>>()
            private val extCandidates = mutableListOf<ExtCandidateSpan>()

            override fun enterNode(ctx: WContext, reporter: WReporter): Boolean {
                when (ctx.type) {
                    WNodeType.CLASS -> classFrames.add(ClassFrame())
                    WNodeType.VALUE_PARAMETER_LIST -> paramListFrames.add(mutableListOf())
                    else -> {}
                }
                return true
            }

            override fun onChildLeaf(ctx: WContext, reporter: WReporter) {
                val ancestors = ctx.ancestors
                when {
                    ctx.type == WNodeType.IDENTIFIER && ancestors.peekType() == WNodeType.CLASS -> {
                        val frame = classFrames.lastOrNull()
                        if (frame != null && !frame.nameCaptured) {
                            frame.nameCaptured = true
                            frame.name = IdentifierCasing.unquote(ctx.leafText ?: "")
                        }
                    }

                    ctx.type == WNodeType.KW_INTERFACE &&
                        ancestors.peekType() == WNodeType.CLASS -> classFrames.lastOrNull()?.isInterface = true

                    ctx.type == WNodeType.IDENTIFIER &&
                        ancestors.peekType() == WNodeType.VALUE_PARAMETER -> paramListFrames
                        .lastOrNull()
                        ?.add(IdentifierCasing.unquote(ctx.leafText ?: ""))

                    else -> {}
                }
            }

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.CLASS -> if (classFrames.isNotEmpty()) classFrames.removeAt(classFrames.size - 1)
                    WNodeType.SUPER_TYPE_LIST -> finalizeSuperTypeList(ctx, children)
                    WNodeType.FUN -> finalizeFun(ctx, children)
                    WNodeType.VALUE_PARAMETER_LIST ->
                        if (paramListFrames.isNotEmpty()) {
                            paramNamesByListStart[ctx.startOffset] = paramListFrames.removeAt(paramListFrames.size - 1)
                        }

                    else -> {}
                }
            }

            private fun finalizeSuperTypeList(ctx: WContext, children: ChildBuffer) {
                val classFrame = classFrames.lastOrNull() ?: return
                if (classFrame.isInterface) return
                for (i in 0 until children.size) {
                    if (children.type(i) != WNodeType.SUPER_TYPE_CALL_ENTRY) continue
                    val baseName = leadingIdentifier(children.textSpan(i, ctx.sourceText)) ?: continue
                    relatedClassPairs.add(classFrame.name to baseName)
                }
            }

            private fun finalizeFun(ctx: WContext, children: ChildBuffer) {
                if (!children.hasChildOfType(WNodeType.TYPE_REFERENCE) || !children.hasChildOfType(WNodeType.DOT)) {
                    return
                }
                val nameIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (nameIdx < 0) return
                val functionName = children.leafText(nameIdx)?.let { IdentifierCasing.unquote(it) } ?: return
                val receiverIdx = children.firstChildOfType(WNodeType.TYPE_REFERENCE)
                if (receiverIdx < 0) return
                val receiverClassName = children.textSpan(receiverIdx, ctx.sourceText).toString()

                val paramListIdx = children.firstChildOfType(WNodeType.VALUE_PARAMETER_LIST)
                val paramNames =
                    if (paramListIdx >= 0) {
                        paramNamesByListStart.remove(children.startOffset(paramListIdx)) ?: emptyList()
                    } else {
                        emptyList()
                    }

                var returnType: String? = null
                val colonIdx = children.firstChildOfType(WNodeType.COLON)
                if (colonIdx >= 0) {
                    for (i in (colonIdx + 1) until children.size) {
                        if (children.type(i) == WNodeType.TYPE_REFERENCE) {
                            returnType = children.textSpan(i, ctx.sourceText).toString()
                            break
                        }
                    }
                }

                val candidate = ExtensionFunctionsSameNameDecision.Candidate(
                    receiverClassName,
                    functionName,
                    paramNames,
                    returnType,
                )
                extCandidates.add(ExtCandidateSpan(candidate, ctx.startOffset, ctx.endOffset))
            }

            private fun leadingIdentifier(text: CharSequence): String? {
                var i = 0
                while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                return if (i == 0) null else text.subSequence(0, i).toString()
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                val candidates = extCandidates.map { it.candidate }
                val pairedIndex = ExtensionFunctionsSameNameDecision.indicesToReport(candidates, relatedClassPairs)
                for ((index, otherIndex) in pairedIndex) {
                    val span = extCandidates[index]
                    val other = extCandidates[otherIndex].candidate
                    val message = ExtensionFunctionsSameNameDecision.message(
                        span.candidate.functionName,
                        span.candidate.receiverClassName,
                        other.receiverClassName,
                    )
                    reporter.report(ruleId, message, span.start, span.end, this)
                }
            }
        }
    }

    private class ClassFrame(
        var nameCaptured: Boolean = false,
        var name: String = "",
        var isInterface: Boolean = false,
    )

    private class ExtCandidateSpan(
        val candidate: ExtensionFunctionsSameNameDecision.Candidate,
        val start: Int,
        val end: Int,
    )
}
