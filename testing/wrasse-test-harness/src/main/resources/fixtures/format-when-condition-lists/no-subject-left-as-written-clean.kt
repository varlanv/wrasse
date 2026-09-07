package sample

fun classify(flag: Boolean, other: Boolean): String {
    return when {
        flag
        -> "yes"
        other -> "maybe"
        else -> "no"
    }
}

// fixture-option: trailing-newline
// expect-clean
