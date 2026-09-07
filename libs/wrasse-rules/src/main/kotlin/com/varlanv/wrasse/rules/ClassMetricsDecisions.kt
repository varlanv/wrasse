package com.varlanv.wrasse.rules

/**
 * A class/interface/object/enum with more than the threshold ([DEFAULT_THRESHOLD] unless
 * configured) directly-declared functions, or a file with more than the same threshold of
 * top-level functions, is reported. A nested class/object's own member functions are never
 * counted toward the enclosing declaration — only directly-declared members count.
 */
object TooManyFunctionsDecision {
    const val DEFAULT_THRESHOLD = 11

    fun decide(
        count: Int,
        kindLabel: String,
        name: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (count <= threshold) return null
        return "$kindLabel '$name' has $count functions; the maximum allowed is $threshold"
    }

    fun decideFile(
        count: Int,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (count <= threshold) return null
        return "File has $count top-level functions; the maximum allowed is $threshold"
    }
}

/**
 * A class/interface/object/enum spanning more than the threshold ([DEFAULT_THRESHOLD] unless
 * configured) distinct source-code lines (excluding blank and comment-only lines) is reported. A
 * nested declaration's own lines never count toward the enclosing one — each gets an independent
 * frame.
 */
object LargeClassDecision {
    const val DEFAULT_THRESHOLD = 600

    fun decide(
        lines: Int,
        name: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (lines <= threshold) return null
        return "Class '$name' is too large ($lines lines); the maximum allowed is $threshold"
    }
}
