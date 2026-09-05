package com.varlanv.wrasse.rules

/**
 * Verdict logic for a nested class/object carrying a redundant explicit `public` modifier inside
 * a top-level `internal` class, compiler-free so it is unit-testable without a kotlinc
 * dependency. [ownerQualifies] is true when the containing declaration is itself a top-level,
 * non-interface class carrying an explicit `internal` modifier — a nested declaration can never
 * be more visible than its container, so an explicit `public` there is misleading rather than
 * effective. Report-only: deleting the modifier is [redundant-visibility-modifier]'s job, not
 * this rule's.
 */
object NestedClassesVisibilityDecision {
    const val MESSAGE = "The explicit 'public' modifier still results in an internal nested class"

    fun decide(
        ownerQualifies: Boolean,
        hasPublic: Boolean,
        hasEnum: Boolean,
        hasCompanion: Boolean,
    ): String? {
        if (!ownerQualifies || !hasPublic || hasEnum || hasCompanion) return null
        return MESSAGE
    }
}
