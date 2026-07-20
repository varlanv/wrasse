package sample

fun classify(x: Int): String {
    return when (x) {
        1 ->
            if (x > 10)
                "big"
            else
                "small"
        2 -> {
            println("computing")
            "two"
        }
        else -> "other"
    }
}

// expect-error 6:13 when-entry-bracing "Missing braces on when-entry body"
// expect-error 7:17 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 9:17 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 14:17 when-entry-bracing "Missing braces on when-entry body"