package com.varlanv.wrasse.rules

/**
 * Verdict logic for a comment carrying one of a fixed set of forbidden markers, compiler-free so
 * it is unit-testable without a kotlinc dependency.
 *
 * The marker set (`TODO:`, `FIXME:`, `STOPSHIP:`) is a hardcoded default; wrasse has no config
 * surface for a project-specific list (only `level`). Matching is a plain, case-sensitive
 * substring search over the comment's own raw text, delimiters included.
 */
object ForbiddenCommentDecision {
    private val MARKERS = listOf("FIXME:", "STOPSHIP:", "TODO:")

    fun decide(commentContent: CharSequence): String? {
        val marker = MARKERS.firstOrNull { commentContent.contains(it) } ?: return null
        return "This comment contains '$marker', which is forbidden in production code"
    }
}
