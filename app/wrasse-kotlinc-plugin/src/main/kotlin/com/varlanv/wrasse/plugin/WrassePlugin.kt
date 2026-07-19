package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.HexEncoding
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchMerge
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.lang.WPatchWriter
import com.varlanv.wrasse.model.RuleLevel
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WCallableUsage
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WQualifiedUsage
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WResolvedImport
import com.varlanv.wrasse.model.WResolvedUsage
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WRuleSet
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import org.jetbrains.kotlin.KtLightSourceElement

private const val PATCH_FILE_NAME = "wrasse-fixes.txt"

class WrassePlugin(
    private val ruleSet: WRuleSet,
    private val fixOutputDir: Path? = null,
    private val globalExclude: List<PathMatcher> = emptyList(),
    private val configDir: Path? = null,
    private val dumpResolvedUsage: Boolean = false,
) {

    private val patchFileLock = Any()
    private var patchEntries: List<FileEdits>? = null

    fun checkFile(
        source: KtLightSourceElement,
        fileName: String,
        sourceFilePath: String,
        resolvedUsage: ((collectQualifiedUsages: Boolean) -> WResolvedUsage)? = null,
    ): List<ViolationReport> {
        val filePath = resolveFilePath(sourceFilePath, fileName)
        if (matchesAny(globalExclude, filePath)) {
            return emptyList()
        }

        val dispatch = ruleSet.dispatchForFile { config -> matchesAny(config.exclude, filePath) }

        val ctx = WContext(filePath = filePath.toString())
        val needsQualifiedUsages = dumpResolvedUsage || ruleSet.requiresQualifiedUsages
        if (resolvedUsage != null && (dumpResolvedUsage || ruleSet.requiresResolution || needsQualifiedUsages)) {
            ctx.resolvedUsage = resolvedUsage(needsQualifiedUsages)
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
                reports.add(
                    ViolationReport(
                        message = "${rule.id}: $message",
                        startOffset = startOffset,
                        endOffset = endOffset,
                        level = rule.config.effectiveLevel,
                    )
                )
                for (edit in edits) {
                    requireWithinOpenAncestor(ctx, ruleId, edit)
                    ctx.editPlan.add(ruleId, edit)
                }
            }
        }
        LightTreeStreamAdapter.walk(
            source = source,
            ctx = ctx,
            dispatch = dispatch,
            reporter = reporter,
        )

        val finalEdits = ctx.editPlan.finalEdits()
        if (fixOutputDir != null) {
            val newEntry = if (finalEdits.isNotEmpty()) {
                FileEdits(filePath.toString(), computeSourceHash(ctx.sourceText), finalEdits)
            } else {
                null
            }
            mergeAndWritePatchFile(fixOutputDir, filePath.toString(), newEntry)
        }

        val usage = ctx.resolvedUsage
        if (dumpResolvedUsage && usage != null) {
            reporter.reports.add(
                ViolationReport(
                    message = dumpMessage(usage),
                    startOffset = 0,
                    endOffset = 0,
                    level = RuleLevel.ERROR,
                )
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
        return "resolved-usage: classifiers=$classifiers callables=$callables imports=$imports qualified=$qualified errors=${usage.hasResolutionErrors}"
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

    private fun requireWithinOpenAncestor(ctx: WContext, ruleId: String, edit: WEdit) {
        if (ctx.ancestors.isEmpty) return
        val ancestorStart = ctx.ancestors.peekStartOffset()
        val ancestorEnd = ctx.ancestors.peekEndOffset()
        check(edit.startOffset >= ancestorStart && edit.endOffset <= ancestorEnd) {
            "EditPlan: rule '$ruleId' emitted an edit ${edit.startOffset}..${edit.endOffset} outside its " +
                "currently open ancestor $ancestorStart..$ancestorEnd"
        }
    }

    /** Stub for the FirFunctionCallChecker hook. Will dispatch to SemanticWRules once the resolution facade lands (Phase B.3). */
    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }

    private fun resolveFilePath(sourceFilePath: String, fileName: String): Path =
        runCatching { Path.of(sourceFilePath).toAbsolutePath().normalize() }
            .getOrElse { Path.of(fileName) }

    private fun matchesAny(matchers: List<PathMatcher>, filePath: Path): Boolean {
        if (matchers.isEmpty()) return false
        val candidate = relativeToConfigDir(filePath)
        return matchers.any { it.matches(candidate) }
    }

    private fun relativeToConfigDir(filePath: Path): Path {
        val dir = configDir ?: return filePath
        return runCatching { dir.relativize(filePath) }.getOrDefault(filePath)
    }

    private fun mergeAndWritePatchFile(dir: Path, filePath: String, newEntry: FileEdits?) {
        synchronized(patchFileLock) {
            val current = patchEntries ?: readExistingPatchEntries(dir)
            val merged = if (newEntry != null) {
                WPatchMerge.upsert(current, newEntry)
            } else {
                WPatchMerge.remove(current, filePath)
            }
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
        val bytes = sourceText.toString().toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return HexEncoding.lowerCase(digest)
    }
}
