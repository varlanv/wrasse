package sample

fun check(a: Boolean, b: Boolean, s: String): Boolean {
    return if (a && b && s == "x && y") true else false
}

// expect-error 4:16 complex-condition "This condition combines 3 boolean operators; the maximum allowed is 2"
