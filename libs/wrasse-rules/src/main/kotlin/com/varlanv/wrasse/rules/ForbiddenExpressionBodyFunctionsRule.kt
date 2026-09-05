package com.varlanv.wrasse.rules

import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig
import com.varlanv.wrasse.model.isWhitespaceOrComment

/**
 * A function declared with an expression body (`fun f(): T = expr`) is reported at its `=`.
 * Autofixed into a block body (see [ForbiddenExpressionBodyDecision]) only when the return type
 * is written explicitly (a block body would otherwise change an inferred type to `Unit`), no
 * comment sits between the return type and the body, and the body is single-line or format is
 * enabled — with format off, the body's own continuation lines could not be re-indented safely.
 * Cannot be on together with `function-expression-body`, which prefers the opposite shape.
 */
class ForbiddenExpressionBodyFunctionsRule : WUninitializedRule {
    override val id: String = "forbidden-expression-body-functions"
    override val canAutofix: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = setOf(WNodeType.FUN)

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                val eqIdx = children.firstChildOfType(WNodeType.EQ)
                if (eqIdx < 0) return
                val eqStart = children.startOffset(eqIdx)
                val eqEnd = children.endOffset(eqIdx)
                val edits = fixEdits(ctx, children, eqIdx)
                reporter.report(ruleId, ForbiddenExpressionBodyDecision.MESSAGE, eqStart, eqEnd, this, edits = edits)
            }

            private fun fixEdits(ctx: WContext, children: ChildBuffer, eqIdx: Int): List<WEdit> {
                val typeIdx = returnTypeIndex(children, eqIdx)
                if (typeIdx < 0) return emptyList()
                var bodyIdx = eqIdx + 1
                while (bodyIdx < children.size && children.type(bodyIdx) == WNodeType.WHITE_SPACE) bodyIdx++
                if (bodyIdx >= children.size || children.type(bodyIdx).isWhitespaceOrComment) return emptyList()
                var beforeIdx = eqIdx - 1
                while (beforeIdx >= 0 && children.type(beforeIdx) == WNodeType.WHITE_SPACE) beforeIdx--
                if (beforeIdx < 0 || children.type(beforeIdx).isWhitespaceOrComment) return emptyList()
                val bodyStart = children.startOffset(bodyIdx)
                val bodyEnd = children.endOffset(bodyIdx)
                val source = ctx.sourceText
                val formatEnabled = config.formatEnabled
                if (!formatEnabled && hasNewline(source, bodyStart, bodyEnd)) return emptyList()
                val typeText = source.subSequence(children.startOffset(typeIdx), children.endOffset(typeIdx))
                return ForbiddenExpressionBodyDecision.edits(
                    gapStart = children.endOffset(beforeIdx),
                    bodyStart = bodyStart,
                    bodyEnd = bodyEnd,
                    returnsUnit = ForbiddenExpressionBodyDecision.isUnitTypeText(typeText),
                    bodyIsThrow = children.type(bodyIdx) == WNodeType.THROW,
                    formatEnabled = formatEnabled,
                    baseIndentColumn = BraceInsertion.physicalLineIndentColumn(source, ctx.startOffset),
                    indentWidth = INDENT_WIDTH,
                )
            }

            private fun returnTypeIndex(children: ChildBuffer, eqIdx: Int): Int {
                val paramsIdx = children.firstChildOfType(WNodeType.VALUE_PARAMETER_LIST)
                if (paramsIdx < 0) return -1
                var i = paramsIdx + 1
                while (i < eqIdx && children.type(i) != WNodeType.COLON) i++
                while (i < eqIdx && children.type(i) != WNodeType.TYPE_REFERENCE) i++
                return if (i < eqIdx) i else -1
            }

            private fun hasNewline(source: CharSequence, start: Int, end: Int): Boolean {
                for (i in start until end) if (source[i] == '\n') return true
                return false
            }
        }
    }

    private companion object {
        const val INDENT_WIDTH = 4
    }
}
