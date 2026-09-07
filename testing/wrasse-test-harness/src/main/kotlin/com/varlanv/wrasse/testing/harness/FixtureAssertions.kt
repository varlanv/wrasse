package com.varlanv.wrasse.testing.harness

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity

fun CompilationResult.assertMatchesExpectations(fixture: Fixture) {
    val wrasse = wrasseDiagnostics
    if (fixture.expectClean) {
        withClue("Expected no wrasse diagnostics but got:\n${formatDiagnostics(wrasse)}") {
            wrasse.shouldBeEmpty()
        }
        return
    }

    withClue(
        "Expected ${fixture.expectations.size} diagnostic(s) but got ${wrasse.size}:\n" +
            "Expected:\n${formatExpectations(fixture.expectations)}\n" +
            "Actual:\n${formatDiagnostics(wrasse)}",
    ) {
        wrasse shouldHaveSize fixture.expectations.size
    }

    val sorted = wrasse.sortedWith(compareBy({ it.location?.line ?: 0 }, { it.location?.column ?: 0 }))
    val expectedSorted = fixture.expectations.sortedWith(compareBy({ it.line }, { it.column }))

    for ((actual, expected) in sorted.zip(expectedSorted)) {
        withClue("Diagnostic at ${expected.line}:${expected.column}") {
            actual.location?.line shouldBe expected.line
            actual.location?.column shouldBe expected.column
            actual.message shouldBe "wrasse: ${expected.ruleId}: ${expected.message}"
            actual.severity shouldBe expected.severity.toCompilerSeverity()
        }
    }
}

private fun ExpectedSeverity.toCompilerSeverity(): CompilerMessageSeverity = when (this) {
    ExpectedSeverity.ERROR -> CompilerMessageSeverity.ERROR
    ExpectedSeverity.WARNING -> CompilerMessageSeverity.WARNING
}

private fun formatDiagnostics(diagnostics: List<TestDiagnostic>): String = diagnostics
    .joinToString("\n") { d ->
        "  ${d.severity} ${d.location?.line}:${d.location?.column} ${d.message}"
    }
    .ifEmpty { "  (none)" }

private fun formatExpectations(expectations: List<ExpectedDiagnostic>): String = expectations.joinToString("\n") { e ->
    "  ${e.severity} ${e.line}:${e.column} ${e.ruleId} \"${e.message}\""
}
