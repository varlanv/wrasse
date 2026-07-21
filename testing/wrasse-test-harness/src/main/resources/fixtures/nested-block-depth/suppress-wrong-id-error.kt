package sample

@Suppress("no-semicolons")
fun f(a: Boolean, b: Boolean, c: Boolean, d: Boolean, e: Boolean) {
    if (a) {
        if (b) {
            if (c) {
                if (d) {
                    if (e) {
                        println("x")
                    }
                }
            }
        }
    }
}

// expect-error 4:5 nested-block-depth "Function 'f' is nested too deeply (depth 5); the maximum allowed is 4"
