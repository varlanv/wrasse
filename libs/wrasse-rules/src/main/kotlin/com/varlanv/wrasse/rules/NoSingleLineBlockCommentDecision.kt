package com.varlanv.wrasse.rules

/**
 * Verdict logic for a single-line block comment with nothing but same-line whitespace after it,
 * compiler-free so it is unit-testable without a kotlinc dependency.
 *
 * [replacement] turns `/* text */` into `// text`: the inner span between the delimiters loses at
 * most one leading and one trailing space (otherwise verbatim — the closing delimiter can never
 * recur inside that span by construction, and an embedded `//` is harmless once the comment
 * already runs to end of line), then gets the `// ` prefix. `null` when that inner span is
 * entirely whitespace (an empty `/* */`), or when [commentText] is too short to hold both
 * delimiters or lacks the closing delimiter (an unterminated block comment at end of file), since
 * there is no well-formed content to carry over.
 */
object NoSingleLineBlockCommentDecision {
    const val MESSAGE = "Replace the block comment with an EOL comment"

    fun decide(commentText: CharSequence, followedByCodeOnSameLine: Boolean): String? {
        if (commentText.contains('\n')) return null
        if (followedByCodeOnSameLine) return null
        return MESSAGE
    }

    fun replacement(commentText: CharSequence): String? {
        val text = commentText.toString()
        if (text.length < 4 || !text.endsWith("*/")) return null
        val inner = text.substring(2, text.length - 2)
        if (inner.isBlank()) return null
        val start = if (inner.startsWith(' ')) 1 else 0
        val end = if (inner.length > start && inner.endsWith(' ')) inner.length - 1 else inner.length
        return "// " + inner.substring(start, end)
    }
}
