package com.varlanv.wrasse.plugin

import com.varlanv.wrasse.lang.FileApplyResult
import com.varlanv.wrasse.lang.WEdit
import com.varlanv.wrasse.testing.BaseSpec
import com.varlanv.wrasse.testing.harness.TestDiagnostic
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.opentest4j.AssertionFailedError

private fun loc(line: Int, column: Int, lineEnd: Int, columnEnd: Int) =
CompilerMessageLocationWithRange.create("test.kt", line, column, lineEnd, columnEnd, "")

private fun diagnostic(message: String, line: Int, column: Int, lineEnd: Int, columnEnd: Int) =
TestDiagnostic(CompilerMessageSeverity.ERROR, message, loc(line, column, lineEnd, columnEnd))

class IdempotenceCycleSpec :
    BaseSpec(
        {
            val sourceText = "line1\nline2\nline3\n"

            should("convert line:column to a character offset assuming LF line endings") {
                IdempotenceCycle.lineColToOffset(sourceText, 1, 1) shouldBe 0
                IdempotenceCycle.lineColToOffset(sourceText, 2, 1) shouldBe 6
                IdempotenceCycle.lineColToOffset(sourceText, 3, 6) shouldBe 17
            }

            should("reconstruct a diagnostic's offset range from its start and end line:column") {
                val range = IdempotenceCycle.diagnosticOffsetRange(sourceText, loc(1, 1, 1, 6))
                range shouldBe 0..5
            }

            should("fall back to a zero-width range when the location carries no end position") {
                val range = IdempotenceCycle.diagnosticOffsetRange(sourceText, loc(2, 1, -1, -1))
                range shouldBe 6..6
            }

            should("exclude a diagnostic whose range overlaps an emitted edit from the survivor set") {
                val fixed = diagnostic("wrasse: fixable-rule: on line1", line = 1, column = 1, lineEnd = 1, columnEnd = 6)
                val flagOnly = diagnostic("wrasse: flag-only-rule: on line3", line = 3, column = 1, lineEnd = 3, columnEnd = 6)
                val survivors = IdempotenceCycle.expectedSurvivorKeys(sourceText, listOf(fixed, flagOnly), listOf(WEdit(0, 5, "")))
                survivors shouldBe listOf("ERROR wrasse: flag-only-rule: on line3")
            }

            should("preserve multiplicity: two identical flag-only diagnostics both survive, two identical fixed ones both vanish") {
                val fixed = diagnostic("wrasse: fixable-rule: on line1", line = 1, column = 1, lineEnd = 1, columnEnd = 6)
                val flagOnly = diagnostic("wrasse: flag-only-rule: on line3", line = 3, column = 1, lineEnd = 3, columnEnd = 6)
                val survivors = IdempotenceCycle
                    .expectedSurvivorKeys(sourceText, listOf(fixed, fixed, flagOnly, flagOnly), listOf(WEdit(0, 5, "")))
                survivors shouldBe listOf("ERROR wrasse: flag-only-rule: on line3", "ERROR wrasse: flag-only-rule: on line3")
            }

            should("pass silently when P1 was applied to every file") {
                IdempotenceCycle.assertPatchFullyApplied(listOf(FileApplyResult.Applied(Path.of("/tmp/sample/test.kt"), editCount = 2)))
            }

            should("fail loudly with the skip reason when P1 application is skipped, not silently pass") {
                val error = shouldThrow<AssertionFailedError> {
                    IdempotenceCycle
                        .assertPatchFullyApplied(
                            listOf(FileApplyResult.Skipped(Path.of("/tmp/sample/test.kt"), "source changed since compilation")),
                        )
                }
                error.message shouldBe (
                "Applying P1 (the fix emitted by round 1) did not fully apply to every file:\n" +
                    "  Skipped: /tmp/sample/test.kt - source changed since compilation\n" +
                    "expected:<true> but was:<false>"
                )
            }

            should("fail loudly with the failure reason when P1 application fails, not silently pass") {
                val error = shouldThrow<AssertionFailedError> {
                    IdempotenceCycle
                        .assertPatchFullyApplied(
                            listOf(FileApplyResult.Failed(Path.of("/tmp/sample/test.kt"), "overlapping edits at 4..8 and 2..6")),
                        )
                }
                error.message shouldBe (
                "Applying P1 (the fix emitted by round 1) did not fully apply to every file:\n" +
                    "  Failed: /tmp/sample/test.kt - overlapping edits at 4..8 and 2..6\n" +
                    "expected:<true> but was:<false>"
                )
            }

            should("pass silently when the second fix pass left no patch file behind") {
                val absentPatchFile = Files.createTempDirectory("idempotence-cycle-spec").resolve("wrasse-fixes.txt")
                IdempotenceCycle.assertNoResidualEdits(absentPatchFile)
            }

            should(
                "pass silently when the patch file exists but holds zero file entries (header-only, self-cleaning under always-on emission)",
            ) {
                val dir = Files.createTempDirectory("idempotence-cycle-spec")
                val patchFile = dir.resolve("wrasse-fixes.txt")
                Files.writeString(patchFile, "# wrasse-fixes v1\n")

                IdempotenceCycle.assertNoResidualEdits(patchFile)
            }

            should("TRIPWIRE: fail loudly, showing the residual patch entries, when a second fix pass is not a fixed point") {
                val dir = Files.createTempDirectory("idempotence-cycle-spec")
                val patchFile = dir.resolve("wrasse-fixes.txt")
                val residualPatchContent = "# wrasse-fixes v1\nfile:/tmp/sample/test.kt\nhash:deadbeef\nedit:4:4:;\n"
                Files.writeString(patchFile, residualPatchContent)

                val error = shouldThrow<AssertionFailedError> {
                    IdempotenceCycle.assertNoResidualEdits(patchFile)
                }
                error.message shouldBe (
                "fix(fix(x)) == fix(x) violated: a second fix pass emitted further edits, expected merge-on-write " +
                    "to have removed every file's patch entry (self-cleaning); the patch file itself may still " +
                    "exist, header-only, since emission now rides check mode unconditionally.\n" +
                    "Residual patch entries at $patchFile:\n  /tmp/sample/test.kt (1 edits)\n" +
                    "expected:<true> but was:<false>"
                )
            }

            should("pass silently when the patched file has no non-wrasse compiler errors") {
                IdempotenceCycle
                    .assertPatchedFileCompiles(
                        listOf(
                            diagnostic("wrasse: some-rule: still flagged", line = 1, column = 1, lineEnd = 1, columnEnd = 2),
                            TestDiagnostic(CompilerMessageSeverity.WARNING, "unused variable", loc(2, 1, 2, 5)),
                        ),
                    )
            }

            should("TRIPWIRE: fail loudly, showing every non-wrasse compiler error, when a fix breaks compilation") {
                val error = shouldThrow<AssertionFailedError> {
                    IdempotenceCycle
                        .assertPatchedFileCompiles(
                            listOf(
                                diagnostic(
                                    "Conflicting import: imported name 'Item' is ambiguous.",
                                    line = 4,
                                    column = 1,
                                    lineEnd = 4,
                                    columnEnd = 20,
                                ),
                                diagnostic("Unresolved reference 'Item'.", line = 6, column = 9, lineEnd = 6, columnEnd = 13),
                                diagnostic("wrasse: some-rule: still flagged", line = 1, column = 1, lineEnd = 1, columnEnd = 2),
                            ),
                        )
                }
                error.message shouldBe (
                "fix(fix(x)) produced code that no longer compiles — an applied patch must never break " +
                    "compilation, even when the breakage carries no wrasse diagnostic of its own.\n" +
                    "Non-wrasse compiler errors after applying the fix:\n" +
                    "  ERROR 4:1 Conflicting import: imported name 'Item' is ambiguous.\n" +
                    "  ERROR 6:9 Unresolved reference 'Item'.\n" +
                    "expected:<true> but was:<false>"
                )
            }

            should("TRIPWIRE: fail loudly when a violation that carried an edit re-appears in D2 (fix is not idempotent)") {
                val error = shouldThrow<AssertionFailedError> {
                    IdempotenceCycle
                        .assertExpectedSurvivors(
                            expectedSurvivors = emptyList(),
                            actualD2Keys = listOf("ERROR wrasse: reintroducing-rule: Unnecessary semicolon"),
                        )
                }
                error.message shouldBe (
                "Idempotence invariant (D19) violated: after applying autofix, D2 must equal exactly the D1 " +
                    "diagnostics that carried no edits.\n" +
                    "Expected D2 (D1 minus fixed violations):\n  (none)\n" +
                    "Actual D2 (after fix(fix(x))):\n  ERROR wrasse: reintroducing-rule: Unnecessary semicolon\n" +
                    "Unexpected elements from index 0\n" +
                    "expected:<[]> but was:<[\"ERROR wrasse: reintroducing-rule: Unnecessary semicolon\"]>"
                )
            }

            should("TRIPWIRE: fail loudly when the fix creates a brand new violation of a different rule") {
                val error = shouldThrow<AssertionFailedError> {
                    IdempotenceCycle
                        .assertExpectedSurvivors(
                            expectedSurvivors = listOf("ERROR wrasse: flag-only-rule: on line3"),
                            actualD2Keys = listOf(
                                "ERROR wrasse: flag-only-rule: on line3",
                                "ERROR wrasse: newly-triggered-rule: introduced by the fix",
                            ),
                        )
                }
                error.message shouldBe (
                "Idempotence invariant (D19) violated: after applying autofix, D2 must equal exactly the D1 " +
                    "diagnostics that carried no edits.\n" +
                    "Expected D2 (D1 minus fixed violations):\n  ERROR wrasse: flag-only-rule: on line3\n" +
                    "Actual D2 (after fix(fix(x))):\n" +
                    "  ERROR wrasse: flag-only-rule: on line3\n" +
                    "  ERROR wrasse: newly-triggered-rule: introduced by the fix\n" +
                    "Unexpected elements from index 1\n" +
                    "expected:<[\"ERROR wrasse: flag-only-rule: on line3\"]> but was:" +
                    "<[\"ERROR wrasse: flag-only-rule: on line3\", \"ERROR wrasse: newly-triggered-rule: introduced by the fix\"]>"
                )
            }
        },
    )
