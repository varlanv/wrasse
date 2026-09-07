package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.AppliedEdit
import com.varlanv.wrasse.lang.ApplyResult
import com.varlanv.wrasse.lang.FileApplyResult
import com.varlanv.wrasse.lang.FileEdits
import com.varlanv.wrasse.lang.ReportedDiagnostic
import com.varlanv.wrasse.lang.WPatchApplier
import com.varlanv.wrasse.lang.WPatchReader
import com.varlanv.wrasse.lang.WReportReader
import com.varlanv.wrasse.lang.WReportReplay
import com.varlanv.wrasse.testing.harness.CompilationResult
import com.varlanv.wrasse.testing.harness.TestDiagnostic
import com.varlanv.wrasse.testing.harness.TestSource
import com.varlanv.wrasse.testing.harness.WrasseTestHarness
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

object IdempotenceCycle {
    private const val PATCH_FILE_NAME = "wrasse-fixes.txt"
    private const val REPORT_FILE_NAME = "wrasse-report.txt"

    /**
     * Bound on the further apply-and-recompile rounds a fixture opting into `multi-pass-fix` may
     * need (a dropped overlapping edit, see [com.varlanv.wrasse.model.EditPlan.resolveOverlaps],
     * is only applied once the surviving edit has cleared the overlap). Every other fixture must
     * reach its fixed point in one round.
     */
    private const val MAX_EXTRA_ROUNDS = 5

    fun runIfFixEmitted(
        harness: WrasseTestHarness,
        workDir: Path,
        fixOutputDir: Path,
        source: TestSource,
        round1: CompilationResult,
        auxSources: List<TestSource> = emptyList(),
        multiPassFix: Boolean = false,
    ): String? {
        val patchFile = fixOutputDir.resolve("patch").resolve(PATCH_FILE_NAME)
        if (!Files.exists(patchFile)) return null

        val edits = WPatchReader.read(Files.readString(patchFile)).flatMap { it.edits }
        if (edits.isEmpty()) return null

        val filePath = harness.sourcePath(workDir, source)
        val round1ReportedDiagnostics = reportedDiagnosticsFor(fixOutputDir, filePath)

        val applyResult = applyPatch(fixOutputDir)
        val firstPatchedContent = Files.readString(filePath)
        var expectedSurvivors = expectedSurvivorKeys(
            round1.wrasseDiagnostics,
            round1ReportedDiagnostics,
            appliedEditsFor(applyResult, filePath),
            firstPatchedContent,
        )

        var latestRound = harness.compile(listOf(TestSource(source.path, firstPatchedContent)) + auxSources, workDir)
        assertNoNewCompileErrors(round1.diagnostics, latestRound.diagnostics)

        var extraRounds = 0
        while (multiPassFix && hasResidualEdits(patchFile)) {
            extraRounds++
            withClue(
                "Autofix did not converge to a fixed point within $MAX_EXTRA_ROUNDS extra pass(es) after the " +
                    "first: a dropped overlapping edit should resolve within a handful of further rounds, not " +
                    "loop indefinitely.",
            ) {
                (extraRounds <= MAX_EXTRA_ROUNDS) shouldBe true
            }
            val roundReportedDiagnostics = reportedDiagnosticsFor(fixOutputDir, filePath)
            val roundApplyResult = applyPatch(fixOutputDir)
            val patchedContent = Files.readString(filePath)
            expectedSurvivors =
                expectedSurvivorKeys(
                    latestRound.wrasseDiagnostics,
                    roundReportedDiagnostics,
                    appliedEditsFor(roundApplyResult, filePath),
                    patchedContent,
                )
            latestRound = harness.compile(listOf(TestSource(source.path, patchedContent)) + auxSources, workDir)
            assertNoNewCompileErrors(round1.diagnostics, latestRound.diagnostics)
        }

        assertNoResidualEdits(patchFile)
        assertExpectedSurvivors(expectedSurvivors, latestRound.wrasseDiagnostics.map { diagnosticKey(it) })

        return firstPatchedContent
    }

    private fun applyPatch(fixOutputDir: Path): ApplyResult {
        val applyResult = WPatchApplier.apply(fixOutputDir)
        assertPatchFullyApplied(applyResult.files)
        return applyResult
    }

    private fun hasResidualEdits(patchFile: Path): Boolean =
        Files.exists(patchFile) && WPatchReader.read(Files.readString(patchFile)).any { it.edits.isNotEmpty() }

    private fun reportedDiagnosticsFor(fixOutputDir: Path, filePath: Path): List<ReportedDiagnostic> {
        val reportFile = fixOutputDir.resolve("patch").resolve(REPORT_FILE_NAME)
        if (!Files.exists(reportFile)) return emptyList()
        return WReportReader
            .read(Files.readString(reportFile))
            .firstOrNull { it.filePath == filePath.toString() }
            ?.diagnostics ?: emptyList()
    }

    private fun appliedEditsFor(applyResult: ApplyResult, filePath: Path): List<AppliedEdit> =
        (applyResult.files.firstOrNull { it.filePath == filePath } as? FileApplyResult.Applied)?.edits ?: emptyList()

    fun diagnosticKey(diagnostic: TestDiagnostic): String =
        "${diagnostic.severity} ${diagnostic.message}"

    /**
     * A round-1 diagnostic is expected to survive round 2 unless [WReportReplay.remap] keeps it out
     * of [round1ReportedDiagnostics]'s survivors: that drops every `fixable` entry outright, and,
     * for the rest, one whose own offset falls inside an edit [appliedEdits] actually applied. The
     * second condition covers what a bare `fixable=0` reading cannot: a finding whose own edit was
     * folded into a wider one (`format` always emits a single whole-file edit, absorbing every edit
     * it spliced in) is still genuinely fixed even though its own group never survives per-group
     * overlap resolution, so its own `fixable` bit alone reads `0`. Matched to [diagnostics] by
     * de-prefixed message text (the rule id is baked into it, so distinct rules never collide), one
     * survivor consumed per matching round-1 diagnostic to preserve multiplicity.
     */
    fun expectedSurvivorKeys(
        diagnostics: List<TestDiagnostic>,
        round1ReportedDiagnostics: List<ReportedDiagnostic>,
        appliedEdits: List<AppliedEdit>,
        firstPatchedContent: CharSequence,
    ): List<String> {
        val survivingCounts = WReportReplay
            .remap(round1ReportedDiagnostics, appliedEdits, firstPatchedContent)
            .groupingBy { it.message }
            .eachCount()
            .toMutableMap()
        return diagnostics.mapNotNull { diagnostic ->
            val message = diagnostic.message.removePrefix("wrasse: ")
            val remaining = survivingCounts[message] ?: 0
            if (remaining <= 0) return@mapNotNull null
            survivingCounts[message] = remaining - 1
            diagnosticKey(diagnostic)
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

    private fun describeResidualEntries(entries: List<FileEdits>): String = entries
        .joinToString("\n") { file ->
            "  ${file.filePath} (${file.edits.size} edits)" +
                file.edits.joinToString(
                    "",
                ) { "\n    [${it.startOffset}, ${it.endOffset}) -> ${it.replacement.take(400)}" }
        }
        .ifEmpty { "  (none)" }

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
