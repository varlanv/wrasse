package sample

fun check(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return if (a && b && c) true else false
}

// expect-error 4:16 complex-condition "This condition combines 2 boolean operators; the maximum allowed is 1"
