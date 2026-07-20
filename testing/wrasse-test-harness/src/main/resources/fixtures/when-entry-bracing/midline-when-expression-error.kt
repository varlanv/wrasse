package sample

fun classify(x: Int): String = when (x) { 1 -> "one"
    2 -> {
        "two"
    }
    else -> "other"
}

// expect-error 3:48 when-entry-bracing "Missing braces on when-entry body"
// expect-error 7:13 when-entry-bracing "Missing braces on when-entry body"