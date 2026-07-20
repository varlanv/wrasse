package sample

fun foo(x: Int, y: Int, z: Int) {}

fun demo(a: Int, b: Int, c: Int, d: Int, bar: Boolean) {
    foo(
        a,
        if (bar) b else
            c,
        d
    )
}

// expect-error 8:18 if-else-bracing "Missing braces on branch of multi-line if-statement"
// expect-error 9:13 if-else-bracing "Missing braces on branch of multi-line if-statement"
