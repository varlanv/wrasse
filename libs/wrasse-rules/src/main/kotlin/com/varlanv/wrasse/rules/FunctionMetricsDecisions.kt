package com.varlanv.wrasse.rules

/** Pluralized "statement" for [count]: singular at exactly one, plural otherwise. */
internal fun statementNoun(count: Int): String = "statement" + if (count == 1) "" else "s"

/**
 * A function with more than the threshold ([DEFAULT_THRESHOLD] unless configured) `return`
 * statements is reported. A function literally named `equals` is exempt (an `equals` override
 * commonly has one `return` per branch by convention). A `return` inside a lambda literal within
 * the function counts toward it — a lambda is transparent, not a frame boundary of its own.
 */
object ReturnCountDecision {
    const val DEFAULT_THRESHOLD = 2

    fun decide(
        count: Int,
        functionName: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (functionName == "equals") return null
        if (count <= threshold) return null
        return "Function '$functionName' has $count return ${statementNoun(count)}; the maximum allowed is $threshold"
    }
}

/** A function with more than the threshold ([DEFAULT_THRESHOLD] unless configured) `throw` statements is reported. */
object ThrowsCountDecision {
    const val DEFAULT_THRESHOLD = 2

    fun decide(
        count: Int,
        functionName: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (count <= threshold) return null
        return "Function '$functionName' has $count throw ${statementNoun(count)}; the maximum allowed is $threshold"
    }
}

/**
 * A function nesting `if`/`when`/`try`/`for`/`while`/`do-while` deeper than the threshold
 * ([DEFAULT_THRESHOLD] unless configured) is reported. An unbraced `else if` continuation never
 * adds its own depth level; a scope-function call with a trailing lambda
 * (`run`/`let`/`apply`/`with`/`also`/`use`/`forEach`) is never treated as an extra nesting level
 * either.
 */
object NestedBlockDepthDecision {
    const val DEFAULT_THRESHOLD = 4

    fun decide(
        maxDepth: Int,
        functionName: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (maxDepth <= threshold) return null
        return "Function '$functionName' is nested too deeply (depth $maxDepth); the maximum allowed is $threshold"
    }
}

/**
 * A function whose McCabe cyclomatic complexity exceeds the threshold ([DEFAULT_THRESHOLD] unless
 * configured) is reported. A nested
 * function (local or anonymous) contributes nothing to the enclosing function's complexity — it
 * is evaluated entirely on its own. Scope-function calls are not treated as an extra decision
 * point either, matching [NestedBlockDepthDecision]'s own nesting rule.
 */
object CyclomaticComplexityDecision {
    const val DEFAULT_THRESHOLD = 14

    fun decide(
        complexity: Int,
        functionName: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (complexity <= threshold) return null
        return "Function '$functionName' has a cyclomatic complexity of $complexity; the maximum allowed is $threshold"
    }
}

/**
 * A function spanning more than the threshold ([DEFAULT_THRESHOLD] unless configured) distinct
 * source-code lines (excluding blank and comment-only lines) is reported, counted over the whole
 * function including its signature.
 */
object LongMethodDecision {
    const val DEFAULT_THRESHOLD = 60

    fun decide(
        lines: Int,
        functionName: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (lines <= threshold) return null
        return "Function '$functionName' is too long ($lines lines); the maximum allowed is $threshold"
    }
}
