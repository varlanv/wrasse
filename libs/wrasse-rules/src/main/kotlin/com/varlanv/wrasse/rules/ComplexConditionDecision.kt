package com.varlanv.wrasse.rules

/**
 * A condition combining [MAX_OPERATORS] or more `&&`/`||` operators is reported (never fixed —
 * extracting a well-named function or variable is an authored decision). Counting is a plain,
 * non-overlapping substring frequency over the condition's own source text.
 */
object ComplexConditionDecision {
    const val MAX_OPERATORS = 3

    fun decide(conditionText: CharSequence): String? {
        val operatorCount = frequency(conditionText, "&&") + frequency(conditionText, "||")
        if (operatorCount < MAX_OPERATORS) return null
        return "This condition combines $operatorCount boolean operators; the maximum allowed is ${MAX_OPERATORS - 1}"
    }

    private fun frequency(source: CharSequence, part: String): Int {
        var count = 0
        var pos = indexOf(source, part, 0)
        while (pos != -1) {
            pos += part.length
            count++
            pos = indexOf(source, part, pos)
        }
        return count
    }

    private fun indexOf(
        source: CharSequence,
        part: String,
        from: Int,
    ): Int {
        val last = source.length - part.length
        outer@ for (i in from..last) {
            for (j in part.indices) {
                if (source[i + j] != part[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
