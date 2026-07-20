package sample

fun classify(flag: Boolean): String {
    return when {
        flag
        -> "yes"
        else -> "no"
    }
}

// expect-clean
