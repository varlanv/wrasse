package sample

fun demo() {
    if (if (true) true else false)
        if (true) true else false
    else
        println(if (true) true else false)
}

// expect-error 5:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 7:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
