package sample

fun f(a: Boolean, b: Boolean) {
    if (a) {
        if (b) {
            println("x")
        }
    }
}

// expect-clean
