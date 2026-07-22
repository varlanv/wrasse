package com.varlanv.wrasse.rules

/**
 * A catch parameter name matching `_` or `(ignore|expected).*` signals an intentionally-unhandled
 * exception, exempting the catch clause from `too-generic-exception-caught`,
 * `empty-catch-block`, and `swallowed-exception` alike — the same default every one of those
 * upstream rules ships.
 */
object AllowedExceptionName {
    private val PATTERN = Regex("_|(ignore|expected).*")

    fun isAllowed(name: String): Boolean = PATTERN.matches(name)
}
