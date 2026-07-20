package sample

fun demo(): Int {
    val result =
        if (true)
            1
        else
            2
    return result
}

// expect-error 6:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 8:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
