package sample

fun f(x: Int) {
    if (x > 0) {
        throw IllegalStateException("a")
    }
    throw IllegalStateException("b")
}

// expect-clean
