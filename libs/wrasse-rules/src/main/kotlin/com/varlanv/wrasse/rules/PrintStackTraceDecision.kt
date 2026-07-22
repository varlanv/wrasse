package com.varlanv.wrasse.rules

/**
 * Verdict logic for `Thread.dumpStack()` and a caught exception's own `.printStackTrace()` call,
 * compiler-free so it is unit-testable without a kotlinc dependency.
 */
object PrintStackTraceDecision {
    const val MESSAGE = "Do not print a stack trace. These debug statements should be removed or replaced with a logger."
}
