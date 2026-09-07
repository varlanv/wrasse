package io.kotest.assertions.throwables

inline fun <reified T : Throwable> shouldThrow(block: () -> Unit): T {
    try {
        block()
    } catch (failure: Throwable) {
        return failure as T
    }
    throw AssertionError("no exception")
}
