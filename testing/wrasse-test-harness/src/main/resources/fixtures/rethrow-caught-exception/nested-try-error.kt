package sample

fun foo() {
    try {
        println("outer")
    } catch (outer: IllegalStateException) {
        try {
            println("inner")
        } catch (inner: IllegalStateException) {
            throw inner
        }
    }
}

// expect-error 10:13 rethrow-caught-exception "Do not rethrow a caught exception of the same type."
