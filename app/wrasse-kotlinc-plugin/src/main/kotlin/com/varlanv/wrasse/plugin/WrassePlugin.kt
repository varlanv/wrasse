package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.HexEncoding
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchWriter
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WContext
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import com.varlanv.wrasse.model.WRuleSet
import org.jetbrains.kotlin.KtLightSourceElement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

class WrassePlugin(
    private val ruleSet: WRuleSet,
    private val fixEnabled: Boolean = false,
    private val fixOutputDir: Path? = null,
    private val globalExclude: List<PathMatcher> = emptyList(),
    private val configDir: Path? = null,
) {

    private val patchFileLock = Any()
    private var patchFileInitialized = false

    fun checkFile(
        source: KtLightSourceElement,
        fileName: String,
        sourceFilePath: String,
    ): List<ViolationReport> {
        val filePath = resolveFilePath(sourceFilePath, fileName)
        if (matchesAny(globalExclude, filePath)) {
            return emptyList()
        }

        val dispatch = ruleSet.dispatchForFile { config -> matchesAny(config.exclude, filePath) }

        val ctx = WContext(filePath = filePath.toString())
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
        if (fixEnabled && finalEdits.isNotEmpty() && fixOutputDir != null) {
            val sourceHash = computeSourceHash(ctx.sourceText)
            val fileEdits = FileEdits(filePath.toString(), sourceHash, finalEdits)
            appendToPatchFile(fileEdits)
        }

        return reporter.reports
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

    private fun appendToPatchFile(fileEdits: FileEdits) {
        synchronized(patchFileLock) {
            val patchFile = fixOutputDir!!.resolve("wrasse-fixes.txt")
            if (!patchFileInitialized) {
                Files.createDirectories(patchFile.parent)
                Files.newBufferedWriter(patchFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { w ->
                    WPatchWriter.writeHeader(w)
                    WPatchWriter.write(w, fileEdits)
                }
                patchFileInitialized = true
            } else {
                Files.newBufferedWriter(patchFile, StandardOpenOption.APPEND).use { w ->
                    WPatchWriter.write(w, fileEdits)
                }
            }
        }
    }

    private fun computeSourceHash(sourceText: CharSequence): String {
        val bytes = sourceText.toString().toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return HexEncoding.lowerCase(digest)
    }
}
