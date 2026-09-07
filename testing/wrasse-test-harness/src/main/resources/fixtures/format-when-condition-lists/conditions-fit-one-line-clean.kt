package sample

fun kind(x: Int): Int {
    return when (x) {
        1, 2, 3 -> 1
        else -> 0
    }
}

// fixture-option: trailing-newline
// expect-clean
