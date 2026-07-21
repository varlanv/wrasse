package sample

@Suppress("nested-block-depth")
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

// expect-clean
