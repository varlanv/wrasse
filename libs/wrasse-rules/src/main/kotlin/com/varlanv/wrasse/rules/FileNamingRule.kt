package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.ChildBuffer
import com.varlanv.wrasse.model.WBufferedNodeRule
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

private val TARGET_TYPES = setOf(
    WNodeType.FILE,
    WNodeType.CLASS,
    WNodeType.OBJECT_DECLARATION,
    WNodeType.FUN,
    WNodeType.PROPERTY,
    WNodeType.TYPEALIAS,
)

/** A file's own name must match its sole non-private top-level class/object, or be PascalCase (see [FileNamingDecision]). */
class FileNamingRule : WUninitializedRule {
    override val id: String = "filename"

    override fun initRule(config: WrasseRuleConfig): WBufferedNodeRule {
        val ruleId = id
        return object : WBufferedNodeRule {
            override val id = ruleId
            override val config = config
            override val targetTypes = TARGET_TYPES

            private val topLevelClassLikeNames = mutableListOf<String>()
            private var otherTopLevelDeclarations = 0

            override fun exitNode(
                ctx: WContext,
                children: ChildBuffer,
                reporter: WReporter,
            ) {
                when (ctx.type) {
                    WNodeType.CLASS, WNodeType.OBJECT_DECLARATION -> recordTopLevelDeclaration(ctx, children)
                    WNodeType.FUN, WNodeType.PROPERTY, WNodeType.TYPEALIAS ->
                        if (isTopLevel(ctx)) otherTopLevelDeclarations++
                    WNodeType.FILE -> finalizeFile(ctx, reporter)
                    else -> {}
                }
            }

            private fun isTopLevel(
                ctx: WContext,
            ): Boolean = ctx.ancestors.size == 1 && ctx.ancestors.peekType() == WNodeType.FILE

            private fun recordTopLevelDeclaration(ctx: WContext, children: ChildBuffer) {
                if (!isTopLevel(ctx)) return
                val idIdx = children.firstChildOfType(WNodeType.IDENTIFIER)
                if (idIdx < 0) return
                val modifierIdx = children.firstChildOfType(WNodeType.MODIFIER_LIST)
                val isPrivate = modifierIdx >= 0 &&
                    WordScan.containsWord(children.textSpan(modifierIdx, ctx.sourceText), "private")
                if (isPrivate) return
                topLevelClassLikeNames.add(IdentifierCasing.unquote(children.textSpan(idIdx, ctx.sourceText)))
            }

            private fun finalizeFile(ctx: WContext, reporter: WReporter) {
                val normalizedPath = ctx.filePath.replace('\\', '/')
                if (!normalizedPath.endsWith(
                    ".kt",
                ) || normalizedPath.endsWith("/package.kt") || normalizedPath == "package.kt") {
                    return
                }
                val fileStem = normalizedPath.substringAfterLast('/').substringBeforeLast('.')
                val singleName = if (otherTopLevelDeclarations == 0) topLevelClassLikeNames.singleOrNull() else null
                val message = FileNamingDecision.decide(fileStem, singleName) ?: return
                reporter.report(ruleId, message, 0, 1, this)
            }
        }
    }
}
