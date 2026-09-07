package sample

fun demo(x: String): Int {
    return when (x) {
        "alpha", "bravo", "charlie", "delta" -> 1
        else -> 0
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
