package sample

fun classify(x: Int): String {
    return when {
        x > 0 -> {
            "positive"
        }
        x < 0 ->
            "negative"
        else -> "zero"
    }
}

// expect-error 9:13 when-entry-bracing "Missing braces on when-entry body"
// expect-error 10:17 when-entry-bracing "Missing braces on when-entry body"