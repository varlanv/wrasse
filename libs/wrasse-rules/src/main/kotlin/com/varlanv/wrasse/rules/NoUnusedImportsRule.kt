package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

class NoUnusedImportsRule : WUninitializedRule {
    override val id: String = "no-unused-imports"
    override val requiresResolution: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private val directives = mutableListOf<ImportRecord>()
            private val commentSpans = mutableListOf<IntRange>()
            private val assembler = ImportDirectiveAssembler()

            override fun enterNode(ctx: WContext) {
                if (ctx.type != WNodeType.IMPORT_DIRECTIVE) return
                assembler.enterImportDirective(ctx.startOffset)
            }

            override fun exitNode(ctx: WContext) {
                if (ctx.type != WNodeType.IMPORT_DIRECTIVE) return
                val raw = assembler.exitImportDirective(ctx.endOffset)
                if (!raw.isStar && raw.pathParts.isNotEmpty()) {
                    directives.add(
                        ImportRecord(
                            fqn = raw.pathParts.joinToString("."),
                            simpleName = raw.pathParts.last(),
                            aliasName = raw.aliasName,
                            startOffset = raw.startOffset,
                            endOffset = raw.endOffset,
                        )
                    )
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT, WNodeType.KDOC -> {
                        commentSpans.add(ctx.startOffset until ctx.endOffset)
                        return
                    }

                    else -> {}
                }
                if (!ctx.hasAncestor(WNodeType.IMPORT_DIRECTIVE)) return
                assembler.visitLeaf(ctx)
            }

            override fun afterFile(ctx: WContext, reporter: WReporter) {
                val usage = ctx.resolvedUsage ?: return
                if (usage.hasResolutionErrors) return
                for (import in directives) {
                    val unused = UnusedImportDecision.isUnused(
                        import = import,
                        classifiers = usage.classifiers,
                        callables = usage.callables,
                        sourceText = ctx.sourceText,
                        commentSpans = commentSpans,
                    )
                    if (unused) {
                        val edit = ImportRemovalSpan.compute(ctx.sourceText, import.startOffset, import.endOffset)
                        val edits = if (edit != null) listOf(edit) else emptyList()
                        reporter.report(ruleId, "Unused import", import.startOffset, import.endOffset, this, edits = edits)
                    }
                }
            }
        }
    }
}
