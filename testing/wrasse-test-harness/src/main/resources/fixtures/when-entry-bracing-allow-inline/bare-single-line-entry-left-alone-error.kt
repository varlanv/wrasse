package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 ->
            "two"
        3 -> "three"
        else -> "other"
    }
}

// expect-error 9:13 when-entry-bracing "Missing braces on multi-line when-entry body"
