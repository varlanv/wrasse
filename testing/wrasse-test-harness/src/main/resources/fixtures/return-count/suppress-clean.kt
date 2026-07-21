package sample

@Suppress("return-count")
fun f(x: Int): Int {
    if (x > 0) {
        return 1
    }
    if (x < 0) {
        return 2
    }
    return 3
}

// expect-clean
