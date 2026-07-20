package sample

fun classify(x: Int): String {
    return when (x) {
        1, 2 -> {
            "small"
        }
        3, 4 ->
            "medium"
        else -> "large"
    }
}

// expect-error 9:13 when-entry-bracing "Missing braces on when-entry body"
// expect-error 10:17 when-entry-bracing "Missing braces on when-entry body"