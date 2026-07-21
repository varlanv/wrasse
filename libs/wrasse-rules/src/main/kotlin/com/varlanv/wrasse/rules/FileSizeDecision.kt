package com.varlanv.wrasse.rules

/** A file longer than [MAX_LINES] lines is reported. */
object FileSizeDecision {
    const val MAX_LINES = 2_000

    fun decide(lineCount: Int): String? {
        if (lineCount <= MAX_LINES) return null
        return "File has $lineCount lines; the maximum allowed is $MAX_LINES"
    }
}
