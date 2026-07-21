package com.varlanv.wrasse.rules

/** `null` means the accessor is out of this rule's scope entirely — no report at all. */
class TrivialAccessorsVerdict(val fixable: Boolean)

/**
 * Verdict logic for a property accessor's own triviality, compiler-free so it is unit-testable
 * without a kotlinc dependency.
 *
 * [decide] reports whenever [isTrivialBody] is true; [TrivialAccessorsVerdict.fixable] is false —
 * fix declined for this occurrence — whenever [hasModifierList] is true, since an annotation or a
 * visibility modifier on the accessor itself may carry real behavior (JVM interop, restricted
 * visibility) that a plain deletion would silently drop.
 */
object TrivialAccessorsDecision {
    const val MESSAGE = "Trivial accessor"

    fun decide(isTrivialBody: Boolean, hasModifierList: Boolean): TrivialAccessorsVerdict? {
        if (!isTrivialBody) return null
        return TrivialAccessorsVerdict(fixable = !hasModifierList)
    }
}
