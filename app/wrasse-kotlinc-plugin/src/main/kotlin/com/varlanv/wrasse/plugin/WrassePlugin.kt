package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.format.DocBuilder
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.NoopPerf
import com.varlanv.wrasse.lang.PerfStore
import com.varlanv.wrasse.lang.ReportedDiagnostic
import com.varlanv.wrasse.lang.ReportedFile
import com.varlanv.wrasse.lang.Sha256
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchStore
import com.varlanv.wrasse.lang.WPerf
import com.varlanv.wrasse.lang.WReportStore
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WCallSite
import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WFormatConfig
import com.varlanv.wrasse.model.WQualifiedUsage
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WResolvedImport
import com.varlanv.wrasse.model.WResolvedUsage
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WRuleSet
import com.varlanv.wrasse.rules.SuppressionCollectorRule
import java.nio.file.Path
import java.nio.file.PathMatcher
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

private const val NO_AUTOFIX_MARKER = " (no autofix for this shape)"

class WrassePlugin(
    private val ruleSet: WRuleSet,
    private val fixOutputDir: Path? = null,
    private val globalExclude: List<PathMatcher> = emptyList(),
    private val configDir: Path? = null,
    private val dumpResolvedUsage: Boolean = false,
    private val formatConfig: WFormatConfig? = null,
    private val formatRun: Boolean = false,
    private val quiet: Boolean = false,
    private val perf: WPerf = NoopPerf,
    private val excludedRoots: List<Path> = emptyList(),
    private val messageCollector: MessageCollector = MessageCollector.NONE,
    private val projectDir: Path? = null,
) {
    private val patchStore: WPatchStore? = fixOutputDir?.let { WPatchStore(it.resolve(WPatchStore.PATCH_DIR_NAME)) }
    private val reportStore: WReportStore? = fixOutputDir?.let { WReportStore(it.resolve(WPatchStore.PATCH_DIR_NAME)) }
    private val perfTitle: String = "compile ${fixOutputDir?.fileName ?: "-"}"

    fun checkFile(
        source: KtLightSourceElement,
        fileName: String,
        sourceFilePath: String,
        resolvedUsage: ((collectQualifiedUsages: Boolean, collectCallSites: Boolean) -> WResolvedUsage)? = null,
    ): List<ViolationReport> {
        val filePath = resolveFilePath(sourceFilePath, fileName)
        if (isUnderExcludedRoot(filePath)) {
            runCatching { patchStore?.clear(filePath.toString()) }
            runCatching { reportStore?.clear(filePath.toString()) }
            return emptyList()
        }
        val configRelativePath = relativeToConfigDir(filePath)
        if (matchesAny(globalExclude, configRelativePath)) {
            return emptyList()
        }
        if (!perf.enabled) {
            return runCatching { checkFileOrThrow(filePath, configRelativePath, source, resolvedUsage) }
                .getOrElse { failure -> internalFailureReports(filePath, failure) }
        }
        val started = System.nanoTime()
        val reports = runCatching { checkFileOrThrow(filePath, configRelativePath, source, resolvedUsage) }
            .getOrElse { failure -> internalFailureReports(filePath, failure) }
        val elapsed = System.nanoTime() - started
        perf.record("phase:total", elapsed)
        perf.record("file:$filePath", elapsed)
        perf.add("count:files", 1)
        perf.add("count:reports", reports.size.toLong())
        fixOutputDir?.let { PerfStore.write(it, perfTitle, perf) }
        return reports
    }

    private inline fun <T> timed(key: String, block: () -> T): T {
        if (!perf.enabled) return block()
        val started = System.nanoTime()
        val result = block()
        perf.record(key, System.nanoTime() - started)
        return result
    }

    /**
     * A wrasse bug (a rule/engine/printer throwing) must never cost the user their build — see
     * design.md §12's D24 amendment to D18. On catch: this file's entire in-progress
     * [ViolationReport] list and [com.varlanv.wrasse.model.EditPlan] are discarded (never
     * partially applied or partially reported) in favor of one attributed message reported
     * directly through the compiler's [MessageCollector] at [CompilerMessageSeverity.INFO] (never
     * as a [ViolationReport], so `-Werror`/`allWarningsAsErrors` can never turn it into a build
     * failure), and the compile proceeds so kotlinc's own checkers still run and report normally.
     */
    private fun internalFailureReports(filePath: Path, failure: Throwable): List<ViolationReport> {
        runCatching { patchStore?.clear(filePath.toString()) }
        runCatching { reportStore?.clear(filePath.toString()) }
        val exceptionType = failure::class.simpleName ?: failure.javaClass.name
        val message = "wrasse internal error while checking this file " +
            "($exceptionType: ${failure.message}); wrasse results for this file were skipped"
        messageCollector.report(
            CompilerMessageSeverity.INFO,
            "wrasse: $message",
            CompilerMessageLocation.create(filePath.toString(), 1, 1, null),
        )
        return emptyList()
    }

    private fun patchStoreFailureReport(store: WPatchStore, failure: Throwable): ViolationReport {
        val exceptionType = failure::class.simpleName ?: failure.javaClass.name
        return ViolationReport(
            message = "wrasse could not update the fix patch ${store.patchFile()} " +
                "($exceptionType: ${failure.message}); this file's diagnostics are reported but will not be autofixed",
            startOffset = 0,
            endOffset = 0,
            level = RuleLevel.WARN,
        )
    }

    private fun reportStoreFailureReport(store: WReportStore, failure: Throwable): ViolationReport {
        val exceptionType = failure::class.simpleName ?: failure.javaClass.name
        return ViolationReport(
            message = "wrasse could not update the diagnostics report ${store.reportFile()} " +
                "($exceptionType: ${failure.message}); this file's diagnostics are reported but a later lint cannot replay them",
            startOffset = 0,
            endOffset = 0,
            level = RuleLevel.WARN,
        )
    }

    private fun reportedFile(
        filePath: Path,
        sourceHash: String,
        sourceText: CharSequence,
        reports: List<ViolationReport>,
    ): ReportedFile {
        val lineStarts = LineIndex(sourceText)
        val diagnostics = reports
            .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
            .map { report ->
                val level = when (report.configuredLevel) {
                    RuleLevel.ERROR -> ReportedDiagnostic.LEVEL_ERROR
                    else -> ReportedDiagnostic.LEVEL_WARN
                }
                ReportedDiagnostic(
                    lineStarts.lineOf(report.startOffset),
                    lineStarts.columnOf(report.startOffset),
                    report.startOffset,
                    level,
                    report.hasAutofix,
                    report.message,
                )
            }
        return ReportedFile(reportFilePath(filePath), sourceHash, diagnostics)
    }

    private fun reportFilePath(filePath: Path): String {
        val root = projectDir ?: return filePath.toString()
        return runCatching { root.relativize(filePath).toString() }.getOrDefault(filePath.toString())
    }

    private class LineIndex(text: CharSequence) {
        private val starts: IntArray

        init {
            var count = 1
            for (i in 0 until text.length) if (text[i] == '\n') count++
            val array = IntArray(count)
            var line = 1
            for (i in 0 until text.length) if (text[i] == '\n') array[line++] = i + 1
            starts = array
        }

        fun lineOf(offset: Int): Int {
            var low = 0
            var high = starts.size - 1
            while (low < high) {
                val mid = (low + high + 1) ushr 1
                if (starts[mid] <= offset) low = mid else high = mid - 1
            }
            return low + 1
        }

        fun columnOf(offset: Int): Int = offset - starts[lineOf(offset) - 1] + 1
    }

    private fun checkFileOrThrow(
        filePath: Path,
        configRelativePath: Path,
        source: KtLightSourceElement,
        resolvedUsage: ((collectQualifiedUsages: Boolean, collectCallSites: Boolean) -> WResolvedUsage)?,
    ): List<ViolationReport> {
        val suppressionCollector = SuppressionCollectorRule()
        val alwaysOn = mutableListOf<WRule>(suppressionCollector)
        var docBuilder: DocBuilder? = null
        if (formatConfig != null && formatConfig.enabled) {
            docBuilder = DocBuilder(formatConfig)
            alwaysOn.add(docBuilder)
        }
        val dispatch = timed("phase:rule-init") {
            ruleSet.dispatchForFile(
                isExcluded = { config -> matchesAny(config.exclude, configRelativePath) },
                alwaysOn = alwaysOn,
                perf = perf,
            )
        }

        val ctx = WContext(filePath = filePath.toString(), configRelativeFilePath = configRelativePath)
        val needsQualifiedUsages = dumpResolvedUsage || ruleSet.requiresQualifiedUsages
        val needsCallSites = dumpResolvedUsage || ruleSet.requiresCallSites
        if (resolvedUsage != null &&
            (dumpResolvedUsage || ruleSet.requiresResolution || needsQualifiedUsages || needsCallSites)) {
            ctx.resolvedUsage = timed("phase:resolved-usage") { resolvedUsage(needsQualifiedUsages, needsCallSites) }
        }
        val reporter = object : WReporter {
            override val reports = mutableListOf<ViolationReport>()
            val recorded = mutableListOf<ViolationReport>()

            override fun report(
                ruleId: String,
                message: String,
                startOffset: Int,
                endOffset: Int,
                rule: WRule,
                edits: List<WEdit>,
            ) {
                if (suppressionCollector.index.isSuppressed(ruleId, startOffset, endOffset)) return
                val declinedAutofix = edits.isEmpty() && ruleId in ruleSet.autofixCapableIds
                val fullMessage = if (declinedAutofix) "$message$NO_AUTOFIX_MARKER" else message
                val report = ViolationReport(
                    message = "${rule.id}: $fullMessage",
                    startOffset = startOffset,
                    endOffset = endOffset,
                    level = rule.config.effectiveLevel,
                    configuredLevel = rule.config.level,
                    hasAutofix = edits.isNotEmpty(),
                )
                recorded.add(report)
                if (!quiet && (!formatRun || edits.isEmpty())) reports.add(report)
                val groupId = ctx.editPlan.newGroupId()
                for (edit in edits) {
                    requireWithinOpenAncestor(ctx, ruleId, edit)
                    ctx.editPlan.add(ruleId, edit, groupId)
                }
            }
        }
        timed("phase:walk") {
            LightTreeStreamAdapter.walk(source = source, ctx = ctx, dispatch = dispatch, reporter = reporter)
        }
        if (perf.enabled) {
            perf.add("count:bytes", ctx.sourceText.length.toLong())
            perf.add("count:lines", ctx.sourceText.count { it == '\n' }.toLong())
        }
        if (docBuilder != null) timed("phase:format-finish") { docBuilder.finish(ctx, reporter, perf) }

        val finalEdits = timed("phase:edit-plan") { ctx.editPlan.finalEdits() }
        val store = patchStore
        if (store != null) {
            timed("phase:patch-store") {
                val sourceHash = Sha256.ofText(ctx.sourceText)
                runCatching {
                    if (finalEdits.isNotEmpty()) {
                        store.record(FileEdits(filePath.toString(), sourceHash, finalEdits))
                    } else {
                        store.clear(filePath.toString())
                    }
                }.onFailure { failure -> reporter.reports.add(patchStoreFailureReport(store, failure)) }
                reportStore?.let { reports ->
                    runCatching {
                        if (reporter.recorded.isEmpty()) {
                            reports.clear(filePath.toString())
                        } else {
                            reports.record(reportedFile(filePath, sourceHash, ctx.sourceText, reporter.recorded))
                        }
                    }.onFailure { failure -> reporter.reports.add(reportStoreFailureReport(reports, failure)) }
                }
            }
        }
        if (perf.enabled) {
            perf.add("count:edits", finalEdits.size.toLong())
            perf.add("count:dropped-edits", ctx.editPlan.droppedEdits().size.toLong())
        }

        val usage = ctx.resolvedUsage
        if (dumpResolvedUsage && usage != null) {
            reporter.reports.add(
                ViolationReport(message = dumpMessage(usage), startOffset = 0, endOffset = 0, level = RuleLevel.ERROR),
            )
        }

        return reporter.reports
    }

    private fun dumpMessage(usage: WResolvedUsage): String {
        val classifiers = usage.classifiers.sorted().joinToString(prefix = "[", postfix = "]")
        val callables = usage.callables.map(::dumpCallable).sorted().joinToString(prefix = "[", postfix = "]")
        val imports = usage.resolvedImports.map(::dumpImport).sorted().joinToString(prefix = "[", postfix = "]")
        val qualified = usage.qualifiedUsages
            .sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
            .map(::dumpQualifiedUsage)
            .joinToString(prefix = "[", postfix = "]")
        val calls = usage.callSites
            .sortedWith(compareBy({ it.callStartOffset }, { it.callEndOffset }))
            .joinToString(prefix = "[", postfix = "]") { dumpCallSite(it) }
        val aliases = usage.typeAliases.entries
            .map { "${it.key}->${it.value}" }
            .sorted()
            .joinToString(prefix = "[", postfix = "]")
        return "resolved-usage: classifiers=$classifiers callables=$callables imports=$imports qualified=$qualified " +
            "calls=$calls aliases=$aliases errors=${usage.hasResolutionErrors}"
    }

    private fun dumpCallSite(site: WCallSite): String {
        val owner = site.calleeClassFqName?.let { "$it/" } ?: "${site.calleePackageFqName}/"
        val stability = if (site.hasStableParameterNames) "stable" else "unstable"
        val arguments = site.arguments.joinToString(",") { argument ->
            "${argument.startOffset}..${argument.endOffset}=" + argument.parameterName +
                (if (argument.isVararg) "*" else "")
        }
        return "${site.callStartOffset}..${site.callEndOffset}:$owner${site.calleeName}:$stability:[$arguments]"
    }

    private fun dumpQualifiedUsage(usage: WQualifiedUsage): String =
        "${usage.startOffset}..${usage.endOffset}:${usage.kind}:${usage.targetFqName}"

    private fun dumpCallable(usage: WCallableUsage): String {
        val owner = usage.classFqName ?: usage.packageFqName
        return "$owner/${usage.name}"
    }

    private fun dumpImport(import: WResolvedImport): String {
        val suffix = if (import.isStarImport) ".*" else ""
        val status = when {
            !import.resolved -> "?unresolved"
            import.resolvedParentClassFqName != null -> "(parent=${import.resolvedParentClassFqName})"
            else -> ""
        }
        return "${import.fqn}$suffix$status"
    }

    private fun requireWithinOpenAncestor(
        ctx: WContext,
        ruleId: String,
        edit: WEdit,
    ) {
        if (ctx.ancestors.isEmpty) return
        val ancestorStart = ctx.ancestors.peekStartOffset()
        val ancestorEnd = ctx.ancestors.peekEndOffset()
        check(edit.startOffset >= ancestorStart && edit.endOffset <= ancestorEnd) {
            "EditPlan: rule '$ruleId' emitted an edit ${edit.startOffset}..${edit.endOffset} outside its " +
                "currently open ancestor $ancestorStart..$ancestorEnd"
        }
    }

    /** Hook for the `FirFunctionCallChecker`; currently always returns null (no rule wired to it yet). */
    @Suppress("unused-parameter")
    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }

    private fun resolveFilePath(
        sourceFilePath: String,
        fileName: String,
    ): Path = runCatching { Path.of(sourceFilePath).toAbsolutePath().normalize() }.getOrElse { Path.of(fileName) }

    private fun matchesAny(matchers: List<PathMatcher>, configRelativePath: Path): Boolean {
        if (matchers.isEmpty()) return false
        return matchers.any { it.matches(configRelativePath) }
    }

    private fun isUnderExcludedRoot(filePath: Path): Boolean {
        if (excludedRoots.isEmpty()) return false
        return excludedRoots.any { filePath.startsWith(it) }
    }

    private fun relativeToConfigDir(filePath: Path): Path {
        val dir = configDir ?: return filePath
        return runCatching { dir.relativize(filePath) }.getOrDefault(filePath)
    }
}
