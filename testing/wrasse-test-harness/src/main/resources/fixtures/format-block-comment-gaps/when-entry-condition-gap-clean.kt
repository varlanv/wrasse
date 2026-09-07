package sample

fun choose(value: Int): String = when (value) {
    /* head */ 0 -> "zero"
    1 /* tail */, 2 -> "small"
    3, /* head */ 4 -> "medium"
    else -> "other"
}

// expect-clean
