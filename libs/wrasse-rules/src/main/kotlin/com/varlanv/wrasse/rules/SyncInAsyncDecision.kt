package com.varlanv.wrasse.rules

/**
 * Verdict logic for a `runBlocking { }` call reached from inside asynchronous code, compiler-free
 * so it is unit-testable without a kotlinc dependency. Matches the upstream rule this derives from
 * exactly: both facts (is this really the `runBlocking { }` trailing-lambda call shape; is there a
 * governing `async`/`launch` call or `suspend` function anywhere in the enclosing scope) are purely
 * textual/structural — no resolution of which coroutine builder `async`/`launch` actually refer to.
 */
object SyncInAsyncDecision {
    const val MESSAGE = "runBlocking() called from inside asynchronous code (async/launch/suspend); this blocks the thread"

    fun decide(isRunBlockingTrailingLambdaCall: Boolean, hasGoverningAsyncContext: Boolean): String? =
        if (isRunBlockingTrailingLambdaCall && hasGoverningAsyncContext) MESSAGE else null
}
