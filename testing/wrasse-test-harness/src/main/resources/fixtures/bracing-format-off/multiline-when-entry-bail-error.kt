package sample

fun demo(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> "two"
            .plus("!")
        else -> "other"
    }
}

// expect-error 8:14 when-entry-bracing "Missing braces on when-entry body (no autofix for this shape)"
// expect-error 10:17 when-entry-bracing "Missing braces on when-entry body"
