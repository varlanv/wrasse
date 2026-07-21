package sample

fun outer() {
    fun inner(x: Int) {
        if (x > 0) {
            throw IllegalStateException("a")
        }
        if (x < 0) {
            throw IllegalStateException("b")
        }
        throw IllegalStateException("c")
    }
    inner(1)
}

// expect-error 4:9 throws-count "Function 'inner' has 3 throw statements; the maximum allowed is 2"
