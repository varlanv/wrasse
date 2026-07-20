package sample

fun demo() = if (condition())
    "a" else
    "b"

fun condition() = true

// expect-error 4:5 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 5:5 if-else-bracing "Missing braces on branch of multi-line if-statement"
