package sample

fun loop(a: Boolean, b: Boolean, c: Boolean, d: Boolean) {
    while (a && b && c && d) {
        break
    }
}

// expect-error 4:12 complex-condition "This condition combines 3 boolean operators; the maximum allowed is 2"
