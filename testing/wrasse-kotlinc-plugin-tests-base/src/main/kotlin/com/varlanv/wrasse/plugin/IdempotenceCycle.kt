package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FileApplyResult
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.testing.harness.CompilationResult
import com.varlanv.wrasse.testing.harness.TestDiagnostic
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import java.nio.file.Files
import java.nio.file.Path

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
        val patchFile = fixOutputDir.resolve(PATCH_FILE_NAME)
        if (!Files.exists(patchFile)) return null

        val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
        if (edits.isEmpty()) return null

        val expectedSurvivors = expectedSurvivorKeys(source.content, round1.wrasseDiagnostics, edits)

        val applyResult = WPatchApplier.apply(fixOutputDir)
        assertPatchFullyApplied(applyResult.files)

        val patchedContent = Files.readString(harness.sourcePath(workDir, source))
        val round2 = harness.compile(listOf(TestSource(source.path, patchedContent)) + auxSources, workDir)

        assertNoResidualEdits(patchFile)
        assertExpectedSurvivors(expectedSurvivors, round2.wrasseDiagnostics.map { diagnosticKey(it) })

        return patchedContent
    }

    fun diagnosticKey(diagnostic: TestDiagnostic): String =
        "${diagnostic.severity} ${diagnostic.message}"

    fun lineColToOffset(sourceText: String, line: Int, column: Int): Int {
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
        val end =
            if (location.lineEnd >= 1 && location.columnEnd >= 1) {
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
                results.joinToString("\n") { describeApplyResult(it) }
        ) {
            results.all { it is FileApplyResult.Applied } shouldBe true
        }
    }

    fun assertNoResidualEdits(patchFile: Path) {
        val residualContent = if (Files.exists(patchFile)) Files.readString(patchFile) else null
        withClue(
            "fix(fix(x)) == fix(x) violated: a second fix pass emitted further edits, " +
                "expected the patch file to be absent.\nResidual patch at $patchFile:\n${residualContent ?: "(absent)"}"
        ) {
            residualContent shouldBe null
        }
    }

    /**
     * Not wired into [runIfFixEmitted] by default: several existing fixtures compile with
     * `noJdk = true` and trip pre-existing, unrelated `Cannot access '...'`/`Unresolved
     * reference 'java'` diagnostics from that classpath choice alone, which this would flag as
     * false positives across the whole fixture suite. Called explicitly by specs that need the
     * stronger guarantee that an emitted edit never introduces a genuine (non-wrasse) compiler
     * error — e.g. a resolution-powered fix whose facade cannot fully distinguish safe from
     * unsafe rewrites.
     */
    fun assertPatchedFileCompiles(diagnostics: List<TestDiagnostic>) {
        val nonWrasseErrors = diagnostics.filter { it.severity.isError && !it.message.startsWith("wrasse:") }
        withClue(
            "fix(fix(x)) produced code that no longer compiles — an applied patch must never break " +
                "compilation, even when the breakage carries no wrasse diagnostic of its own.\n" +
                "Non-wrasse compiler errors after applying the fix:\n${formatDiagnostics(nonWrasseErrors)}"
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
                "Actual D2 (after fix(fix(x))):\n${formatKeys(actualSorted)}"
        ) {
            actualSorted shouldBe expectedSorted
        }
    }

    private fun IntRange.overlapsInclusive(other: IntRange): Boolean =
        first <= other.last && other.first <= last

    private fun describeApplyResult(result: FileApplyResult): String =
        when (result) {
            is FileApplyResult.Applied -> "  Applied: ${result.filePath} (${result.editCount} edits)"
            is FileApplyResult.Skipped -> "  Skipped: ${result.filePath} - ${result.reason}"
            is FileApplyResult.Failed -> "  Failed: ${result.filePath} - ${result.reason}"
        }

    private fun formatKeys(keys: List<String>): String =
        keys.joinToString("\n") { "  $it" }.ifEmpty { "  (none)" }

    private fun formatDiagnostics(diagnostics: List<TestDiagnostic>): String =
        diagnostics.joinToString("\n") { "  ${it.severity} ${it.location?.line}:${it.location?.column} ${it.message}" }
            .ifEmpty { "  (none)" }
}
