package sample

fun f(a: Boolean, b: Boolean, c: Boolean) {
    if (a) {
        if (b) {
            if (c) {
                println("x")
            }
        }
    }
}

// expect-error 3:5 nested-block-depth "Function 'f' is nested too deeply (depth 3); the maximum allowed is 2"
