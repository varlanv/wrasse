package com.varlanv.wrasse.rules

/**
 * A function with more than [MAX] `return` statements is reported. A function literally named
 * `equals` is exempt (an `equals` override commonly has one `return` per branch by convention).
 * A `return` inside a lambda literal within the function counts toward it — a lambda is
 * transparent, not a frame boundary of its own.
 */
object ReturnCountDecision {
    const val MAX = 2

    fun decide(count: Int, functionName: String): String? {
        if (functionName == "equals") return null
        if (count <= MAX) return null
        return "Function '$functionName' has $count return statements; the maximum allowed is $MAX"
    }
}

/** A function with more than [MAX] `throw` statements is reported. */
object ThrowsCountDecision {
    const val MAX = 2

    fun decide(count: Int, functionName: String): String? {
        if (count <= MAX) return null
        return "Function '$functionName' has $count throw statements; the maximum allowed is $MAX"
    }
}

/**
 * A function nesting `if`/`when`/`try`/`for`/`while`/`do-while` deeper than [MAX_DEPTH] is
 * reported. An unbraced `else if` continuation never adds its own depth level; a scope-function
 * call with a trailing lambda (`run`/`let`/`apply`/`with`/`also`/`use`/`forEach`) is never treated
 * as an extra nesting level either.
 */
object NestedBlockDepthDecision {
    const val MAX_DEPTH = 4

    fun decide(maxDepth: Int, functionName: String): String? {
        if (maxDepth <= MAX_DEPTH) return null
        return "Function '$functionName' is nested too deeply (depth $maxDepth); the maximum allowed is $MAX_DEPTH"
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
 * A function spanning more than [MAX_LINES] distinct source-code lines (excluding blank and
 * comment-only lines) is reported, counted over the whole function including its signature.
 */
object LongMethodDecision {
    const val MAX_LINES = 60

    fun decide(lines: Int, functionName: String): String? {
        if (lines <= MAX_LINES) return null
        return "Function '$functionName' is too long ($lines lines); the maximum allowed is $MAX_LINES"
    }
}
