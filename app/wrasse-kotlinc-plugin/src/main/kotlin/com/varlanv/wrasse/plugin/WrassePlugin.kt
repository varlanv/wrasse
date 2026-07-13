package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.adapter.LightTreeStreamAdapter
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchWriter
import com.varlanv.wrasse.model.StreamDispatch
import com.varlanv.wrasse.model.ViolationReport
import com.varlanv.wrasse.model.WReporter
import com.varlanv.wrasse.model.WRule
import org.jetbrains.kotlin.KtLightSourceElement
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

class WrassePlugin(
    private val dispatch: StreamDispatch,
    private val fixEnabled: Boolean = false,
    private val fixOutputDir: Path? = null,
) {

    private var patchFileInitialized = false

    fun checkFile(
        source: KtLightSourceElement,
        fileName: String,
        sourceFilePath: String,
    ): List<ViolationReport> {
        val collectedEdits = mutableListOf<WEdit>()
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
                collectedEdits.addAll(edits)
            }
        }
        LightTreeStreamAdapter.walk(
            source = source,
            filePath = fileName,
            dispatch = dispatch,
            reporter = reporter,
        )

        if (fixEnabled && collectedEdits.isNotEmpty() && fixOutputDir != null) {
            val sourceHash = computeSourceHash(sourceFilePath)
            val fileEdits = FileEdits(sourceFilePath, sourceHash, collectedEdits)
            appendToPatchFile(fileEdits)
        }

        return reporter.reports
    }

    /** Stub for the FirFunctionCallChecker hook. Will dispatch to SemanticWRules once the resolution facade lands (Phase B.3). */
    fun checkCall(packageName: String, callableName: String): String? {
        return null
    }

    private fun appendToPatchFile(fileEdits: FileEdits) {
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

    private fun computeSourceHash(sourceFilePath: String): String {
        val bytes = Files.readAllBytes(Path.of(sourceFilePath))
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
