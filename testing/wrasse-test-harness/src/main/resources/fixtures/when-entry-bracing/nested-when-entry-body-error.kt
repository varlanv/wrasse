package sample

fun classify(x: Int, y: Boolean): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> when (y) {
            true -> "yes"
            false -> {
                "no"
            }
        }
        else -> "other"
    }
}

// expect-error 8:14 when-entry-bracing "Missing braces on when-entry body (no autofix for this shape)"
// expect-error 9:21 when-entry-bracing "Missing braces on when-entry body"
// expect-error 14:17 when-entry-bracing "Missing braces on when-entry body"