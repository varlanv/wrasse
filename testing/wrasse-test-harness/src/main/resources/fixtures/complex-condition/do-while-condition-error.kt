package sample

fun loop(a: Boolean, b: Boolean, c: Boolean, d: Boolean) {
    do {
        break
    } while (a && b && c && d)
}

// expect-error 6:14 complex-condition "This condition combines 3 boolean operators; the maximum allowed is 2"
