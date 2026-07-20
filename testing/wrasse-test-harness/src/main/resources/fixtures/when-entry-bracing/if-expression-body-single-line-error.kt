package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> if (x > 0) "pos" else "neg"
        else -> "other"
    }
}

// expect-error 8:14 when-entry-bracing "Missing braces on when-entry body"
// expect-error 9:17 when-entry-bracing "Missing braces on when-entry body"