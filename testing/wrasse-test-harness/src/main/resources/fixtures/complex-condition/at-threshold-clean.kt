package sample

fun check(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return if (a && b && c) true else false
}

// expect-clean
