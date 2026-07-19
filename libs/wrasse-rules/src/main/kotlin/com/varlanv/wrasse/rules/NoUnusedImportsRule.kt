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
            private val starImports = mutableListOf<StarImportRecord>()
            private val commentSpans = mutableListOf<IntRange>()
            private val kdocSpans = mutableListOf<IntRange>()
            private val writtenIdentifiers = mutableSetOf<String>()
            private val assembler = ImportDirectiveAssembler()
            private var packagePathParts = mutableListOf<String>()
            private var filePackageFqName = ""

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> assembler.enterImportDirective(ctx.startOffset)
                    WNodeType.PACKAGE_DIRECTIVE -> packagePathParts = mutableListOf()
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> {
                        val raw = assembler.exitImportDirective(ctx.endOffset)
                        if (raw.pathParts.isEmpty()) return
                        if (raw.isStar) {
                            starImports.add(StarImportRecord(raw.pathParts.joinToString("."), raw.startOffset, raw.endOffset))
                        } else {
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

                    WNodeType.PACKAGE_DIRECTIVE -> filePackageFqName = packagePathParts.joinToString(".")
                    else -> {}
                }
            }

            override fun visitLeaf(ctx: WContext, reporter: WReporter) {
                when (ctx.type) {
                    WNodeType.EOL_COMMENT, WNodeType.BLOCK_COMMENT -> {
                        commentSpans.add(ctx.startOffset until ctx.endOffset)
                        return
                    }

                    WNodeType.KDOC -> {
                        commentSpans.add(ctx.startOffset until ctx.endOffset)
                        kdocSpans.add(ctx.startOffset until ctx.endOffset)
                        return
                    }

                    else -> {}
                }
                if (ctx.hasAncestor(WNodeType.IMPORT_DIRECTIVE)) {
                    assembler.visitLeaf(ctx)
                    return
                }
                if (ctx.type != WNodeType.IDENTIFIER) return
                val text = ctx.leafText?.toString()?.removeSurrounding("`") ?: return
                if (ctx.hasAncestor(WNodeType.PACKAGE_DIRECTIVE)) {
                    packagePathParts.add(text)
                } else {
                    writtenIdentifiers.add(text)
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
                        val edit = ImportRemovalSpan.compute(ctx.sourceText, import.startOffset, import.endOffset)
                        val edits = if (edit != null) listOf(edit) else emptyList()
                        reporter.report(ruleId, "Unused import", import.startOffset, import.endOffset, this, edits = edits)
                    }
                }
                for (star in starImports) {
                    val verdict = UnusedStarDecision.decide(
                        star = star,
                        allStars = starImports,
                        explicitImports = directives,
                        filePackageFqName = filePackageFqName,
                        classifiers = usage.classifiers,
                        callables = usage.callables,
                        writtenIdentifiers = writtenIdentifiers,
                        kdocSpans = kdocSpans,
                        sourceText = ctx.sourceText,
                    )
                    if (verdict is UnusedStarVerdict.Unused) {
                        val edits = if (verdict.edit != null) listOf(verdict.edit) else emptyList()
                        reporter.report(ruleId, "Unused import", star.startOffset, star.endOffset, this, edits = edits)
                    }
                }
            }
        }
    }
}
