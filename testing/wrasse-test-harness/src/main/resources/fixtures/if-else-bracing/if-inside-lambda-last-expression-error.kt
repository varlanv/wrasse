package sample

fun demo(s: String?): Int {
    val i = s?.let {
        if (it == "")
            1
        else
            2
    } ?: 0
    return i
}

// expect-error 6:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 8:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
