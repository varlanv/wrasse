package com.varlanv.wrasse.rules

/** A file longer than the threshold ([DEFAULT_THRESHOLD] unless configured) lines is reported. */
object FileSizeDecision {
    const val DEFAULT_THRESHOLD = 2_000

    fun decide(
        lineCount: Int,
        threshold: Int = DEFAULT_THRESHOLD,
    ): String? {
        if (lineCount <= threshold) return null
        return "File has $lineCount lines; the maximum allowed is $threshold"
    }
}
