package com.varlanv.wrasse.rules

import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WNodeType
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WStreamRule
import com.varlanv.wrasse.model.WUninitializedRule
import com.varlanv.wrasse.model.WrasseRuleConfig

class NoWildcardImportsRule : WUninitializedRule {
    override val id: String = "no-wildcard-imports"
    override val requiresResolution: Boolean = true

    override fun initRule(config: WrasseRuleConfig): WStreamRule {
        val ruleId = id
        return object : WStreamRule {
            override val id = ruleId
            override val config = config

            private val explicitImports = mutableListOf<ImportRecord>()
            private val starImports = mutableListOf<StarImportRecord>()
            private val kdocSpans = mutableListOf<IntRange>()
            private val writtenIdentifiers = mutableSetOf<String>()
            private val importAssembler = ImportDirectiveAssembler()
            private var packagePathParts = mutableListOf<String>()
            private var filePackageFqName = ""

            override fun enterNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> importAssembler.enterImportDirective(ctx.startOffset)
                    WNodeType.PACKAGE_DIRECTIVE -> packagePathParts = mutableListOf()
                    else -> {}
                }
            }

            override fun exitNode(ctx: WContext) {
                when (ctx.type) {
                    WNodeType.IMPORT_DIRECTIVE -> {
                        val raw = importAssembler.exitImportDirective(ctx.endOffset)
                        if (raw.pathParts.isEmpty()) return
                        val fqn = raw.pathParts.joinToString(".")
                        if (raw.isStar) {
                            starImports.add(StarImportRecord(fqn, raw.startOffset, raw.endOffset))
                        } else {
                            explicitImports.add(
                                ImportRecord(
                                    fqn = fqn,
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
                if (ctx.type == WNodeType.KDOC) {
                    kdocSpans.add(ctx.startOffset until ctx.endOffset)
                    return
                }
                if (ctx.hasAncestor(WNodeType.IMPORT_DIRECTIVE)) {
                    importAssembler.visitLeaf(ctx)
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
                if (starImports.isEmpty()) return
                val usage = ctx.resolvedUsage
                val duplicatePackages = starImports
                    .groupingBy { it.packageFqName }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                for (star in starImports) {
                    val edit = if (usage == null || usage.hasResolutionErrors) {
                        null
                    } else {
                        WildcardExpansionDecision.decide(
                            star = star,
                            duplicatePackages = duplicatePackages,
                            explicitImports = explicitImports,
                            filePackageFqName = filePackageFqName,
                            classifiers = usage.classifiers,
                            callables = usage.callables,
                            writtenIdentifiers = writtenIdentifiers,
                            kdocSpans = kdocSpans,
                            sourceText = ctx.sourceText,
                        )
                    }
                    reporter.report(
                        ruleId,
                        "Replace wildcard import with explicit imports",
                        star.startOffset,
                        star.endOffset,
                        this,
                        edits = if (edit != null) listOf(edit) else emptyList(),
                    )
                }
            }
        }
    }
}
