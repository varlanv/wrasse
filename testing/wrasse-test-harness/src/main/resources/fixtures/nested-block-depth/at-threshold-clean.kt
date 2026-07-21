package sample

fun f(a: Boolean, b: Boolean, c: Boolean, d: Boolean) {
    if (a) {
        if (b) {
            if (c) {
                if (d) {
                    println("x")
                }
            }
        }
    }
}

// expect-clean
