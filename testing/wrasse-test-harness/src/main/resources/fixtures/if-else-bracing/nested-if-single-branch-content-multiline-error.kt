package sample

fun demo() {
    if (true)
        if (true) true else false
}

// expect-error 5:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
