package sample

fun f(x: Int) {
    if (x > 0) {
        throw IllegalStateException("a")
    }
    throw IllegalStateException("b")
}

// expect-error 3:5 throws-count "Function 'f' has 2 throw statements; the maximum allowed is 1"
