package com.varlanv.wrasse.bench

/**
 * One benchmark project: [targetLines] of generated Kotlin in a single Gradle module, as many
 * regular files as it takes plus the pathological files scaled by [stress].
 */
class SizeSpec(
    val name: String,
    val targetLines: Int,
    val stress: StressSpec,
)

class StressSpec(
    val hugeFileFunctions: Int,
    val longFunctionStatements: Int,
    val nestingDepth: Int,
    val longLineChars: Int,
    val rawStringLines: Int,
) {
    companion object {
        val NONE = StressSpec(0, 0, 0, 0, 0)
        val SMALL = StressSpec(
            hugeFileFunctions = 300,
            longFunctionStatements = 1_000,
            nestingDepth = 25,
            longLineChars = 2_000,
            rawStringLines = 500,
        )
        val FULL = StressSpec(
            hugeFileFunctions = 3_000,
            longFunctionStatements = 5_000,
            nestingDepth = 40,
            longLineChars = 6_000,
            rawStringLines = 3_000,
        )
    }
}

object Sizes {
    val ALL = listOf(
        SizeSpec("5k", targetLines = 5_000, stress = StressSpec.NONE),
        SizeSpec("50k", targetLines = 50_000, stress = StressSpec.SMALL),
        SizeSpec("1m", targetLines = 1_000_000, stress = StressSpec.FULL),
    )

    fun byName(name: String): SizeSpec = ALL.firstOrNull { it.name == name }
        ?: error("unknown size '$name', expected one of ${ALL.map { it.name }}")
}
