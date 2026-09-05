package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FileApplyResult
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.testing.harness.CompilationResult
import com.varlanv.wrasse.testing.harness.TestDiagnostic
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation

object IdempotenceCycle {
    private const val PATCH_FILE_NAME = "wrasse-fixes.txt"

    fun runIfFixEmitted(
        harness: WrasseTestHarness,
        workDir: Path,
        fixOutputDir: Path,
        source: TestSource,
        round1: CompilationResult,
        auxSources: List<TestSource> = emptyList(),
    ): String? {
        val patchFile = fixOutputDir.resolve("patch").resolve(PATCH_FILE_NAME)
        if (!Files.exists(patchFile)) return null

        val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
        if (edits.isEmpty()) return null

        val expectedSurvivors = expectedSurvivorKeys(source.content, round1.wrasseDiagnostics, edits)

        val applyResult = WPatchApplier.apply(fixOutputDir)
        assertPatchFullyApplied(applyResult.files)

        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
        val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)) + auxSources, workDir)

        assertNoNewCompileErrors(round1.diagnostics, round2.diagnostics)
        assertNoResidualEdits(patchFile)
        assertExpectedSurvivors(expectedSurvivors, round2.wrasseDiagnostics.map { diagnosticKey(it) })

        return patchedContent
    }

    fun diagnosticKey(diagnostic: TestDiagnostic): String =
        "${diagnostic.severity} ${diagnostic.message}"

    fun lineColToOffset(
        sourceText: String,
        line: Int,
        column: Int,
    ): Int {
        var offset = 0
        var currentLine = 1
        while (currentLine < line) {
            val next = sourceText.indexOf('\n', offset)
            require(next >= 0) {
                "line $line is out of range for source with ${sourceText.count { it == '\n' } + 1} line(s)"
            }
            offset = next + 1
            currentLine++
        }
        return offset + (column - 1)
    }

    fun diagnosticOffsetRange(sourceText: String, location: CompilerMessageSourceLocation?): IntRange? {
        if (location == null) return null
        val start = lineColToOffset(sourceText, location.line, location.column)
        val end = if (location.lineEnd >= 1 && location.columnEnd >= 1) {
            lineColToOffset(sourceText, location.lineEnd, location.columnEnd)
        } else {
            start
        }
        return if (start <= end) start..end else end..start
    }

    fun expectedSurvivorKeys(
        sourceText: String,
        diagnostics: List<TestDiagnostic>,
        edits: List<WEdit>,
    ): List<String> {
        val editRanges = edits.map { it.startOffset..it.endOffset }
        return diagnostics.mapNotNull { diagnostic ->
            val range = diagnosticOffsetRange(sourceText, diagnostic.location)
            val carriedEdit = range != null && editRanges.any { it.overlapsInclusive(range) }
            if (carriedEdit) null else diagnosticKey(diagnostic)
        }
    }

    fun assertPatchFullyApplied(results: List<FileApplyResult>) {
        withClue(
            "Applying P1 (the fix emitted by round 1) did not fully apply to every file:\n" +
                results.joinToString("\n") { describeApplyResult(it) },
        ) {
            results.all { it is FileApplyResult.Applied } shouldBe true
        }
    }

    /**
     * `fix(fix(x)) == fix(x)` holds iff the patch holds zero file entries. Checked against the
     * parsed entries, not file existence — merge-on-write guarantees a recompiled file's own entry
     * is removed, but the patch file itself may still exist (header-only) after a clean recompile.
     */
    fun assertNoResidualEdits(patchFile: Path) {
        val residualEntries =
            if (Files.exists(patchFile)) WPatchReader.read(Files.readString(patchFile)) else emptyList()
        withClue(
            "fix(fix(x)) == fix(x) violated: a second fix pass emitted further edits, expected merge-on-write " +
            "to have removed every file's patch entry (self-cleaning); the patch file itself may still " +
                "exist, header-only, since emission now rides check mode unconditionally.\n" +
                "Residual patch entries at $patchFile:\n${describeResidualEntries(residualEntries)}",
        ) {
            residualEntries.isEmpty() shouldBe true
        }
    }

    private fun describeResidualEntries(entries: List<FileEdits>): String = entries.joinToString("\n") { file ->
        "  ${file.filePath} (${file.edits.size} edits)" +
            file.edits.joinToString("") { "\n    [${it.startOffset}, ${it.endOffset}) -> ${it.replacement.take(400)}" }
    }.ifEmpty { "  (none)" }

    /**
     * Fails if applying the fix introduced a non-wrasse `e:`-severity diagnostic message present in
     * [round2] but absent from [round1]. Compares message *sets* rather than requiring [round2] to
     * be error-free, so pre-existing classpath diagnostics shared by both rounds (e.g. fixtures
     * compiled with `noJdk = true`) don't false-positive.
     */
    fun assertNoNewCompileErrors(round1: List<TestDiagnostic>, round2: List<TestDiagnostic>) {
        val round1Messages = nonWrasseErrorMessages(round1)
        val round2Messages = nonWrasseErrorMessages(round2)
        val newMessages = round2Messages - round1Messages
        val newDiagnostics = round2.filter { it.message in newMessages }.distinctBy { it.message }
        withClue(
            "Autofix broke the compile: round 2 (after applying round 1's emitted edits and " +
            "recompiling) introduced non-wrasse compiler error(s) that round 1 did not have. " +
            "A fix must never turn compiling code into code that no longer compiles.\n" +
                "New errors introduced by the fix:\n${formatDiagnostics(newDiagnostics)}\n" +
                "Round 1 non-wrasse errors:\n${formatDiagnostics(round1.filter { it.message in round1Messages })}",
        ) {
            newMessages.isEmpty() shouldBe true
        }
    }

    private fun nonWrasseErrorMessages(
        diagnostics: List<TestDiagnostic>,
    ): Set<String> = diagnostics
        .filter { it.severity.isError && !it.message.startsWith("wrasse:") }
        .map { it.message }
        .toSet()

    /**
     * Stronger check than [assertNoNewCompileErrors]: fails on any non-wrasse compiler error,
     * without exempting `noJdk = true` fixtures. Not wired into [runIfFixEmitted] by default — call
     * explicitly from specs that need this guarantee.
     */
    fun assertPatchedFileCompiles(diagnostics: List<TestDiagnostic>) {
        val nonWrasseErrors = diagnostics.filter { it.severity.isError && !it.message.startsWith("wrasse:") }
        withClue(
            "fix(fix(x)) produced code that no longer compiles — an applied patch must never break " +
                "compilation, even when the breakage carries no wrasse diagnostic of its own.\n" +
                "Non-wrasse compiler errors after applying the fix:\n${formatDiagnostics(nonWrasseErrors)}",
        ) {
            nonWrasseErrors.isEmpty() shouldBe true
        }
    }

    fun assertExpectedSurvivors(expectedSurvivors: List<String>, actualD2Keys: List<String>) {
        val expectedSorted = expectedSurvivors.sorted()
        val actualSorted = actualD2Keys.sorted()
        withClue(
            "Idempotence invariant (D19) violated: after applying autofix, D2 must equal exactly " +
            "the D1 diagnostics that carried no edits.\n" +
                "Expected D2 (D1 minus fixed violations):\n${formatKeys(expectedSorted)}\n" +
                "Actual D2 (after fix(fix(x))):\n${formatKeys(actualSorted)}",
        ) {
            actualSorted shouldBe expectedSorted
        }
    }

    private fun IntRange.overlapsInclusive(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    private fun describeApplyResult(result: FileApplyResult): String = when (result) {
        is FileApplyResult.Applied -> "  Applied: ${result.filePath} (${result.editCount} edits)"
        is FileApplyResult.Skipped -> "  Skipped: ${result.filePath} - ${result.reason}"
        is FileApplyResult.Failed -> "  Failed: ${result.filePath} - ${result.reason}"
    }

    private fun formatKeys(keys: List<String>): String = keys.joinToString("\n") { "  $it" }.ifEmpty { "  (none)" }

    private fun formatDiagnostics(diagnostics: List<TestDiagnostic>): String = diagnostics
        .joinToString("\n") { "  ${it.severity} ${it.location?.line}:${it.location?.column} ${it.message}" }
        .ifEmpty { "  (none)" }
}
