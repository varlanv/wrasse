package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        // comment before two
        2 ->
            "two"
        else -> "other"
    }
}

// expect-error 10:13 when-entry-bracing "Missing braces on when-entry body"
// expect-error 11:17 when-entry-bracing "Missing braces on when-entry body"