package sample

fun demo(x: Int): String {
    val s =
        if (x > 0)
            // comment1
            "a"
        else
            // comment2
            "b"
    return s
}

// expect-error 7:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 10:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
