package sample

fun demo(x: Int): Int {
    return when {
        x > 0 ->
            if (true)
                1
            else
                2
        else -> 0
    }
}

// expect-error 7:17 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 9:17 if-else-bracing "Missing braces on branch of multi-line if-statement"
