package com.varlanv.wrasse.rules

/**
 * Splits a call-shaped expression's own raw text (e.g. `"pkg.Exception(cause)"`) into its simple
 * callee name (qualification dropped) and its argument-list text, compiler-free so it is
 * unit-testable without a kotlinc dependency. `null` when the text has no top-level `(` at all
 * (not a call), or the parens don't close at the text's own end (a trailing call somewhere inside
 * a larger expression, not this whole span being itself a single call).
 */
object CallShapeText {
    fun parse(text: String): CallShapeFacts? {
        val trimmed = text.trim()
        val openIdx = trimmed.indexOf('(')
        if (openIdx < 0 || !trimmed.endsWith(")")) return null
        val calleePart = trimmed.substring(0, openIdx).trim()
        if (calleePart.isEmpty()) return null
        val simpleName = calleePart.substringAfterLast('.')
        val argsText = trimmed.substring(openIdx + 1, trimmed.length - 1)
        return CallShapeFacts(simpleName = simpleName, argsText = argsText)
    }
}

class CallShapeFacts(val simpleName: String, val argsText: String) {
    /** Top-level argument count: 0 for a blank argument list, otherwise 1 + top-level commas. */
    val argumentCount: Int get() = if (argsText.isBlank()) 0 else 1 + TopLevelCommaCount.count(argsText)
}

/**
 * Counts commas at bracket depth 0 within a call's own argument-list text, treating any run
 * between matching double quotes as opaque (its commas never count) — a lexer-lite
 * approximation, not a real tokenizer: it does not understand string-escape sequences, so a
 * quote escaped with `\"` inside a string argument can throw off depth tracking for the
 * remainder of that argument list. Good enough for the argument-count checks in this batch,
 * which only need "0", "1", or "more than 1", never an exact count above that.
 */
object TopLevelCommaCount {
    fun count(text: String): Int {
        var depth = 0
        var inString = false
        var commas = 0
        for (ch in text) {
            when {
                inString -> if (ch == '"') inString = false
                ch == '"' -> inString = true
                ch == '(' || ch == '[' || ch == '{' -> depth++
                ch == ')' || ch == ']' || ch == '}' -> depth--
                ch == ',' && depth == 0 -> commas++
                else -> {}
            }
        }
        return commas
    }
}
