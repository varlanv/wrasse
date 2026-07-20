package sample

fun test(): Int {
    val b = foo()

    if (b)
        return 1
    else
        return 2
}

fun foo() = true

// expect-error 7:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 9:9 if-else-bracing "Missing braces on branch of multi-line if-statement"
