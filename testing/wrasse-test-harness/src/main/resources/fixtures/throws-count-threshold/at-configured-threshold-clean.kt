package sample

fun f(x: Int) {
    throw IllegalStateException("a")
}

// expect-clean
