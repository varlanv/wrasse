package com.varlanv.wrasse.rules

/**
 * Verdict logic for a bare debug-output call, compiler-free so it is unit-testable without a
 * kotlinc dependency.
 */
object DebugPrintDecision {
    fun message(calleeName: String): String =
    "'$calleeName()' looks like leftover debug output; remove it or replace it with a logger."
}
