package com.varlanv.wrasse.rules

/**
 * Verdict logic for a KDoc's own `@deprecated` block tag, compiler-free so it is unit-testable
 * without a kotlinc dependency. `@deprecated` has no effect in Kotlin's own KDoc tooling — Kotlin
 * expresses deprecation via the `@Deprecated` annotation instead.
 */
object KdocDeprecatedTagDecision {
    const val MESSAGE =
        "The @deprecated tag block does not properly report deprecation in Kotlin, use @Deprecated annotation instead"

    private val TAG_PATTERN = Regex("""@deprecated\b""")

    fun hasDeprecatedTag(kdocText: CharSequence): Boolean = TAG_PATTERN.containsMatchIn(kdocText)
}
