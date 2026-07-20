package sample

fun classify(x: Int): String {
    return when (x) {
        1,
        2 -> {
            "small"
        }
        3 -> "three"
        else -> "other"
    }
}

// expect-error 9:14 when-entry-bracing "Missing braces on when-entry body"
// expect-error 10:17 when-entry-bracing "Missing braces on when-entry body"