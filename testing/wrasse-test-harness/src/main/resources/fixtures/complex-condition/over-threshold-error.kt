package sample

fun check(a: Boolean, b: Boolean, c: Boolean, d: Boolean): Boolean {
    return if (a && b && c && d) true else false
}

// expect-error 4:16 complex-condition "This condition combines 3 boolean operators; the maximum allowed is 2"
