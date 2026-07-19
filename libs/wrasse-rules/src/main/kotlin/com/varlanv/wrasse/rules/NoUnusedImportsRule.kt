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

            private var pathParts = mutableListOf<String>()
            private var aliasName: String? = null
            private var mulSeen = false
            private var directiveStart = -1

            override fun enterNode(ctx: WContext) {
                if (ctx.type != WNodeType.IMPORT_DIRECTIVE) return
                pathParts = mutableListOf()
                aliasName = null
                mulSeen = false
                directiveStart = ctx.startOffset
            }

            override fun exitNode(ctx: WContext) {
                if (ctx.type != WNodeType.IMPORT_DIRECTIVE) return
                if (!mulSeen && pathParts.isNotEmpty()) {
                    directives.add(
                        ImportRecord(
                            fqn = pathParts.joinToString("."),
                            simpleName = pathParts.last(),
                            aliasName = aliasName,
                            startOffset = directiveStart,
                            endOffset = ctx.endOffset,
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
                when (ctx.type) {
                    WNodeType.MUL -> mulSeen = true
                    WNodeType.IDENTIFIER -> {
                        val text = ctx.leafText?.toString()?.removeSurrounding("`") ?: return
                        if (ctx.hasAncestor(WNodeType.IMPORT_ALIAS)) {
                            aliasName = text
                        } else {
                            pathParts.add(text)
                        }
                    }

                    else -> {}
                }
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
                        reporter.report(ruleId, "Unused import", import.startOffset, import.endOffset, this)
                    }
                }
            }
        }
    }
}
