package sample

fun outer(): Int {
    fun inner(x: Int): Int {
        if (x > 0) {
            return 1
        }
        if (x < 0) {
            return 2
        }
        return 3
    }
    return inner(1)
}

// expect-error 4:9 return-count "Function 'inner' has 3 return statements; the maximum allowed is 2"
