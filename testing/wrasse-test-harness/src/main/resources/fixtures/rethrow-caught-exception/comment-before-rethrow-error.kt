package sample

fun foo() {
    try {
        println("work")
    } catch (e: IllegalStateException) {
        throw e
    } catch (e: Exception) {
        // some comment
        throw e
    }
}

// expect-error 7:9 rethrow-caught-exception "Do not rethrow a caught exception of the same type."
// expect-error 10:9 rethrow-caught-exception "Do not rethrow a caught exception of the same type."
