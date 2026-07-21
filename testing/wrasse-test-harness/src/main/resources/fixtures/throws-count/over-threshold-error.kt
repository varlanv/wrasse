package sample

fun f(x: Int) {
    if (x > 0) {
        throw IllegalStateException("a")
    }
    if (x < 0) {
        throw IllegalStateException("b")
    }
    throw IllegalStateException("c")
}

// expect-error 3:5 throws-count "Function 'f' has 3 throw statements; the maximum allowed is 2"
