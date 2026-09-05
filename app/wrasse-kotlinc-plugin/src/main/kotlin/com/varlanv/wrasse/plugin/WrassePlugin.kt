package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.format.DocBuilder
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.HexEncoding
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchMerge
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.lang.WPatchWriter
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
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import org.jetbrains.kotlin.KtLightSourceElement

private const val PATCH_FILE_NAME = "wrasse-fixes.txt"
private const val NO_AUTOFIX_MARKER = " (no autofix for this shape)"
private const val HASH_CHUNK_BYTES = 8192

class WrassePlugin(
    private val ruleSet: WRuleSet,
    private val fixOutputDir: Path? = null,
    private val globalExclude: List<PathMatcher> = emptyList(),
    private val configDir: Path? = null,
    private val dumpResolvedUsage: Boolean = false,
    private val formatConfig: WFormatConfig? = null,
) {
    private val patchFileLock = Any()
    private var patchEntries: List<FileEdits>? = null

    fun checkFile(
        source: KtLightSourceElement,
        fileName: String,
        sourceFilePath: String,
        resolvedUsage: ((collectQualifiedUsages: Boolean, collectCallSites: Boolean) -> WResolvedUsage)? = null,
    ): List<ViolationReport> {
        val filePath = resolveFilePath(sourceFilePath, fileName)
        val configRelativePath = relativeToConfigDir(filePath)
        if (matchesAny(globalExclude, configRelativePath)) {
            return emptyList()
        }

        return runCatching { checkFileOrThrow(filePath, configRelativePath, source, resolvedUsage) }
            .getOrElse { failure -> internalFailureReports(filePath, failure) }
    }

    /**
     * A wrasse bug (a rule/engine/printer throwing) must never cost the user their build — see
     * design.md §12's D24 amendment to D18. On catch: this file's entire in-progress
     * [ViolationReport] list and [com.varlanv.wrasse.model.EditPlan] are discarded (never
     * partially applied or partially reported) in favor of one attributed warning, and the
     * compile proceeds so kotlinc's own checkers still run and report normally.
     */
    private fun internalFailureReports(filePath: Path, failure: Throwable): List<ViolationReport> {
        if (fixOutputDir != null) {
            mergeAndWritePatchFile(fixOutputDir, filePath.toString(), null)
        }
        val exceptionType = failure::class.simpleName ?: failure.javaClass.name
        return listOf(
            ViolationReport(
                message = "wrasse internal error while checking this file " +
                    "($exceptionType: ${failure.message}); wrasse results for this file were skipped",
                startOffset = 0,
                endOffset = 0,
                level = RuleLevel.WARN,
            ),
        )
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
        val dispatch = ruleSet.dispatchForFile(
            isExcluded = { config -> matchesAny(config.exclude, configRelativePath) },
            alwaysOn = alwaysOn,
        )

        val ctx = WContext(filePath = filePath.toString(), configRelativeFilePath = configRelativePath)
        val needsQualifiedUsages = dumpResolvedUsage || ruleSet.requiresQualifiedUsages
        val needsCallSites = dumpResolvedUsage || ruleSet.requiresCallSites
        if (resolvedUsage != null &&
            (dumpResolvedUsage || ruleSet.requiresResolution || needsQualifiedUsages || needsCallSites)) {
            ctx.resolvedUsage = resolvedUsage(needsQualifiedUsages, needsCallSites)
        }
        val reporter = object : WReporter {
            override val reports = mutableListOf<ViolationReport>()

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
                reports.add(
                    ViolationReport(
                        message = "${rule.id}: $fullMessage",
                        startOffset = startOffset,
                        endOffset = endOffset,
                        level = rule.config.effectiveLevel,
                    ),
                )
                for (edit in edits) {
                    requireWithinOpenAncestor(ctx, ruleId, edit)
                    ctx.editPlan.add(ruleId, edit)
                }
            }
        }
        LightTreeStreamAdapter.walk(source = source, ctx = ctx, dispatch = dispatch, reporter = reporter)
        docBuilder?.finish(ctx, reporter)

        val finalEdits = ctx.editPlan.finalEdits()
        if (fixOutputDir != null) {
            val newEntry =
                if (finalEdits.isNotEmpty()) {
                    FileEdits(filePath.toString(), computeSourceHash(ctx.sourceText), finalEdits)
                } else {
                    null
                }
            mergeAndWritePatchFile(fixOutputDir, filePath.toString(), newEntry)
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
        return "resolved-usage: classifiers=$classifiers callables=$callables imports=$imports qualified=$qualified calls=$calls errors=${usage.hasResolutionErrors}"
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
        val status =
            when {
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

    private fun relativeToConfigDir(filePath: Path): Path {
        val dir = configDir ?: return filePath
        return runCatching { dir.relativize(filePath) }.getOrDefault(filePath)
    }

    private fun mergeAndWritePatchFile(
        dir: Path,
        filePath: String,
        newEntry: FileEdits?,
    ) {
        synchronized(patchFileLock) {
            val current = patchEntries ?: readExistingPatchEntries(dir)
            val merged =
                if (newEntry != null) {
                    WPatchMerge.upsert(current, newEntry)
                } else {
                    WPatchMerge.remove(current, filePath)
                }
            if (merged === current && patchEntries != null) return
            patchEntries = merged
            writePatchFileAtomically(dir, merged)
        }
    }

    private fun readExistingPatchEntries(dir: Path): List<FileEdits> {
        val patchFile = dir.resolve(PATCH_FILE_NAME)
        if (!Files.exists(patchFile)) return emptyList()
        return WPatchReader.read(Files.readString(patchFile))
    }

    private fun writePatchFileAtomically(dir: Path, entries: List<FileEdits>) {
        Files.createDirectories(dir)
        val patchFile = dir.resolve(PATCH_FILE_NAME)
        val tmpFile = dir.resolve("$PATCH_FILE_NAME.tmp")
        Files.newBufferedWriter(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { w ->
            WPatchWriter.writeAll(w, entries)
        }
        Files.move(tmpFile, patchFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun computeSourceHash(sourceText: CharSequence): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val encoder = Charsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val input = CharBuffer.wrap(sourceText)
        val output = ByteBuffer.allocate(HASH_CHUNK_BYTES)
        while (true) {
            val result = encoder.encode(input, output, true)
            output.flip()
            digest.update(output)
            output.clear()
            if (result.isUnderflow) break
        }
        encoder.flush(output)
        output.flip()
        digest.update(output)
        return HexEncoding.lowerCase(digest.digest())
    }
}
