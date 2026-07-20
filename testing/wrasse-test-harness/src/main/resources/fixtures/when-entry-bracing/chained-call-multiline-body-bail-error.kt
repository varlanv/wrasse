package sample

fun classify(x: String): String {
    return when (x) {
        "a" -> {
            "one"
        }
        "b" -> x
            .plus("!")
            .plus("?")
        else -> "other"
    }
}

// expect-error 8:16 when-entry-bracing "Missing braces on when-entry body (no autofix for this shape)"
// expect-error 11:17 when-entry-bracing "Missing braces on when-entry body"