package sample

fun check(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return a && b && (a && c)
}

// expect-clean
