package com.varlanv.wrasse.rules

/**
 * A class/interface/object/enum with more than [MAX] directly-declared functions, or a file with
 * more than [MAX] top-level functions, is reported. A nested class/object's own member functions
 * are never counted toward the enclosing declaration — only directly-declared members count.
 */
object TooManyFunctionsDecision {
    const val MAX = 11

    fun decide(count: Int, kindLabel: String, name: String): String? {
        if (count <= MAX) return null
        return "$kindLabel '$name' has $count functions; the maximum allowed is $MAX"
    }

    fun decideFile(count: Int): String? {
        if (count <= MAX) return null
        return "File has $count top-level functions; the maximum allowed is $MAX"
    }
}

/**
 * A class/interface/object/enum spanning more than [MAX_LINES] distinct source-code lines
 * (excluding blank and comment-only lines) is reported. A nested declaration's own lines never
 * count toward the enclosing one — each gets an independent frame.
 */
object LargeClassDecision {
    const val MAX_LINES = 600

    fun decide(lines: Int, name: String): String? {
        if (lines <= MAX_LINES) return null
        return "Class '$name' is too large ($lines lines); the maximum allowed is $MAX_LINES"
    }
}
