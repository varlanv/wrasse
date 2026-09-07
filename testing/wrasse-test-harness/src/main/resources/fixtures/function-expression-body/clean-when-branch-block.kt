package sample

fun foo(x: Int): String {
    val result = when (x) {
        0 -> {
            return "zero"
        }
        else -> "other"
    }
    return result
}

// expect-clean
