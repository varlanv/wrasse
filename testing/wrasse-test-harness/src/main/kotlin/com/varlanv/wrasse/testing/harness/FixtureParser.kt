package com.varlanv.wrasse.testing.harness

object FixtureParser {
    private val EXPECT_DIAGNOSTIC_PATTERN = Regex("""^//\s*expect-(error|warning)\s+(\d+):(\d+)\s+([\w-]+)\s+"([^"]*)"$""")
    private const val EXPECT_CLEAN = "// expect-clean"
    private const val OPTION_TRAILING_NEWLINE = "// fixture-option: trailing-newline"
    private const val OPTION_WARN_ONLY = "// fixture-option: warn-only"
    private val AUX_FILE_PATTERN = Regex("""^//\s*fixture-aux-file:\s*(\S+)$""")

    fun parse(source: String): ParsedFixture {
        val lines = source.lines()
        val expectations = mutableListOf<ExpectedDiagnostic>()
        var expectClean = false
        var trailingNewline = false
        var warnOnly = false
        val auxFiles = mutableListOf<String>()
        val sourceLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == EXPECT_CLEAN) {
                expectClean = true
                continue
            }
            if (trimmed == OPTION_TRAILING_NEWLINE) {
                trailingNewline = true
                continue
            }
            if (trimmed == OPTION_WARN_ONLY) {
                warnOnly = true
                continue
            }
            val auxMatch = AUX_FILE_PATTERN.matchEntire(trimmed)
            if (auxMatch != null) {
                auxFiles.add(auxMatch.groupValues[1])
                continue
            }
            val match = EXPECT_DIAGNOSTIC_PATTERN.matchEntire(trimmed)
            if (match != null) {
                expectations.add(
                    ExpectedDiagnostic(
                        severity = when (match.groupValues[1]) {
                            "warning" -> ExpectedSeverity.WARNING
                            else -> ExpectedSeverity.ERROR
                        },
                        line = match.groupValues[2].toInt(),
                        column = match.groupValues[3].toInt(),
                        ruleId = match.groupValues[4],
                        message = match.groupValues[5],
                    ),
                )
                continue
            }
            sourceLines.add(line)
        }

        while (sourceLines.isNotEmpty() && sourceLines.last().isBlank()) {
            sourceLines.removeLast()
        }

        require(expectClean || expectations.isNotEmpty()) {
            "Fixture must have at least one // expect-error, // expect-warning, or // expect-clean directive"
        }
        require(!(expectClean && expectations.isNotEmpty())) {
            "Fixture cannot have both // expect-clean and // expect-error/expect-warning directives"
        }

        var strippedSource = sourceLines.joinToString("\n")
        if (trailingNewline) {
            strippedSource += "\n"
        }

        return ParsedFixture(
            strippedSource = strippedSource,
            expectations = expectations,
            expectClean = expectClean,
            warnOnly = warnOnly,
            auxFiles = auxFiles,
        )
    }
}

class ParsedFixture(
    val strippedSource: String,
    val expectations: List<ExpectedDiagnostic>,
    val expectClean: Boolean,
    val warnOnly: Boolean = false,
    val auxFiles: List<String> = emptyList(),
)

enum class ExpectedSeverity {
    ERROR,
    WARNING,
}

class ExpectedDiagnostic(val severity: ExpectedSeverity, val line: Int, val column: Int, val ruleId: String, val message: String)
