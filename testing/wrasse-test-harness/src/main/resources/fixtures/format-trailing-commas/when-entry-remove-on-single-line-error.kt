package sample

fun demo(x: Int): String {
    return when (x) {
        1, 2, -> "small"
        else -> "large"
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
