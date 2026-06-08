package com.varlanv.wrasse.testing.harness

object FixtureParser {

    private val EXPECT_ERROR_PATTERN = Regex(
        """^//\s*expect-error\s+(\d+):(\d+)\s+([\w-]+)\s+"([^"]*)"$"""
    )
    private const val EXPECT_CLEAN = "// expect-clean"
    private const val OPTION_TRAILING_NEWLINE = "// fixture-option: trailing-newline"

    fun parse(source: String): ParsedFixture {
        val lines = source.lines()
        val expectations = mutableListOf<ExpectedDiagnostic>()
        var expectClean = false
        var trailingNewline = false
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
            val match = EXPECT_ERROR_PATTERN.matchEntire(trimmed)
            if (match != null) {
                expectations.add(
                    ExpectedDiagnostic(
                        line = match.groupValues[1].toInt(),
                        column = match.groupValues[2].toInt(),
                        ruleId = match.groupValues[3],
                        message = match.groupValues[4],
                    )
                )
                continue
            }
            sourceLines.add(line)
        }

        while (sourceLines.isNotEmpty() && sourceLines.last().isBlank()) {
            sourceLines.removeLast()
        }

        require(expectClean || expectations.isNotEmpty()) {
            "Fixture must have at least one // expect-error or // expect-clean directive"
        }
        require(!(expectClean && expectations.isNotEmpty())) {
            "Fixture cannot have both // expect-clean and // expect-error directives"
        }

        var strippedSource = sourceLines.joinToString("\n")
        if (trailingNewline) {
            strippedSource += "\n"
        }

        return ParsedFixture(
            strippedSource = strippedSource,
            expectations = expectations,
            expectClean = expectClean,
        )
    }
}

class ParsedFixture(
    val strippedSource: String,
    val expectations: List<ExpectedDiagnostic>,
    val expectClean: Boolean,
)

class ExpectedDiagnostic(
    val line: Int,
    val column: Int,
    val ruleId: String,
    val message: String,
)
