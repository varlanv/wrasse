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

            private var lastSignificantLeafType: WNodeType? = null
            private val classBodyOwnerEnumStack = mutableListOf<Boolean>()

            /**
             * True exactly while the next significant token is the semicolon standing in for a
             * `for`/`while`'s empty [WNodeType.BODY] or an `if`'s empty [WNodeType.THEN]: that
             * semicolon is required syntax (removing it is a compile break), never "unnecessary".
             */
            private var pendingBareConstructBody = false

            override fun enterNode(ctx: WContext) {
                if (ctx.type == WNodeType.CLASS || ctx.type == WNodeType.OBJECT_DECLARATION) {
                    classBodyOwnerEnumStack.add(false)
                }
                if (ctx.startOffset == ctx.endOffset && !ctx.ancestors.isEmpty) {
                    val parent = ctx.ancestors.peekType()
                    val isEmptyLoopBody =
                        ctx.type == WNodeType.BODY && (parent == WNodeType.FOR || parent == WNodeType.WHILE)
                    val isEmptyThen = ctx.type == WNodeType.THEN && parent == WNodeType.IF
                    if (isEmptyLoopBody || isEmptyThen) {
                        pendingBareConstructBody = true
                    }
                }
            }

            override fun exitNode(ctx: WContext) {
                if (ctx.type == WNodeType.CLASS || ctx.type == WNodeType.OBJECT_DECLARATION) {
                    classBodyOwnerEnumStack.removeAt(classBodyOwnerEnumStack.size - 1)
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                if (ctx.type == WNodeType.KW_ENUM && classBodyOwnerEnumStack.isNotEmpty()) {
                    classBodyOwnerEnumStack[classBodyOwnerEnumStack.size - 1] = true
                }

                if (ctx.type == WNodeType.SEMICOLON) {
                    handleSemicolon(ctx, reporter)
                }

                if (!ctx.type.isWhitespaceOrComment) {
                    pendingBareConstructBody = false
                    lastSignificantLeafType = ctx.type
                }
            }

            /**
             * Every semicolon is classified and, if unnecessary, reported in the same dispatch
             * as the semicolon leaf itself (via [SemicolonNecessityScan] over [WContext.sourceText]),
             * never deferred to a later leaf event: [WContext.ancestors] only spans the currently
             * open node, so a report issued from a later leaf (once its own, narrower, ancestor is
             * open) would fail the framework's edit-containment check for an edit that belongs to
             * an earlier, already-closed sibling.
             */
            private fun handleSemicolon(ctx: WContext, reporter: WReporter) {
                if (lastSignificantLeafType == WNodeType.KW_OBJECT) return
                if (pendingBareConstructBody) return
                val parentType = if (ctx.ancestors.isEmpty) null else ctx.ancestors.peekType()

                val isEnumTail = (parentType == WNodeType.CLASS_BODY || parentType == WNodeType.ENUM_ENTRY) &&
                    classBodyOwnerEnumStack.lastOrNull() == true
                val unnecessary = if (isEnumTail) {
                    SemicolonNecessityScan.enumTailIsUnnecessary(ctx.sourceText, ctx.endOffset)
                } else {
                    SemicolonNecessityScan.genericIsUnnecessary(ctx.sourceText, ctx.endOffset)
                }
                if (unnecessary) {
                    reporter.report(
                        ruleId, "Unnecessary semicolon",
                        ctx.startOffset, ctx.endOffset, this,
                        edits = listOf(WEdit(ctx.startOffset, ctx.endOffset, ""))
                    )
                }
            }
        }
    }
}

/**
 * Classifies a semicolon's necessity by scanning the source text right after it, skipping
 * whitespace/comments (and, in the generic case, annotation entries) to find the next
 * significant character.
 */
private object SemicolonNecessityScan {

    /**
     * A statement-terminating semicolon is unnecessary unless: another semicolon follows on
     * the same line (the current one is then redundant relative to that one, not required by
     * it), real code follows on the same line (the semicolon is acting as a statement
     * separator), or the next significant token after crossing a newline is `{` (removing the
     * semicolon would let that block literal bind as a trailing lambda of the previous call).
     */
    fun genericIsUnnecessary(text: CharSequence, from: Int): Boolean {
        val length = text.length
        var i = from
        var sawNewline = false
        while (i < length) {
            val c = text[i]
            when {
                c == '\n' -> {
                    sawNewline = true
                    i++
                }

                c == ' ' || c == '\t' || c == '\r' -> i++

                c == '/' && i + 1 < length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < length && text[i] != '\n') i++
                }

                c == '/' && i + 1 < length && text[i + 1] == '*' -> {
                    i = skipBlockComment(text, i)
                }

                c == '@' -> {
                    i = skipAnnotationEntry(text, i)
                }

                else -> {
                    return when {
                        c == ';' -> true
                        !sawNewline -> false
                        c == '{' -> false
                        else -> true
                    }
                }
            }
        }
        return true
    }

    /**
     * An enum entries-list terminator is unnecessary only if nothing but the enum's own
     * closing brace follows it; any further declaration means the semicolon is the required
     * separator between the entries list and the class body's members.
     */
    fun enumTailIsUnnecessary(text: CharSequence, from: Int): Boolean {
        val length = text.length
        var i = from
        while (i < length) {
            val c = text[i]
            when {
                c == ' ' || c == '\t' || c == '\r' || c == '\n' -> i++

                c == '/' && i + 1 < length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < length && text[i] != '\n') i++
                }

                c == '/' && i + 1 < length && text[i + 1] == '*' -> {
                    i = skipBlockComment(text, i)
                }

                else -> return c == '}'
            }
        }
        return false
    }

    private fun skipBlockComment(text: CharSequence, start: Int): Int {
        val length = text.length
        var i = start + 2
        while (i + 1 < length && !(text[i] == '*' && text[i + 1] == '/')) i++
        return minOf(i + 2, length)
    }

    private fun skipAnnotationEntry(text: CharSequence, start: Int): Int {
        val length = text.length
        var i = start + 1
        while (i < length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '.')) i++
        while (i < length && (text[i] == ' ' || text[i] == '\t')) i++
        if (i < length && text[i] == '(') {
            var depth = 1
            i++
            while (i < length && depth > 0) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                i++
            }
        }
        return i
    }
}
