package com.varlanv.wrasse.rules

/**
 * Verdict logic for a single-line block comment with nothing but same-line whitespace after it,
 * compiler-free so it is unit-testable without a kotlinc dependency.
 */
object NoSingleLineBlockCommentDecision {
    const val MESSAGE = "Replace the block comment with an EOL comment"

    fun decide(commentText: CharSequence, followedByCodeOnSameLine: Boolean): String? {
        if (commentText.contains('\n')) return null
        if (followedByCodeOnSameLine) return null
        return MESSAGE
    }
}
