package sample

@Suppress("throws-count")
fun f(x: Int) {
    if (x > 0) {
        throw IllegalStateException("a")
    }
    if (x < 0) {
        throw IllegalStateException("b")
    }
    throw IllegalStateException("c")
}

// expect-clean
