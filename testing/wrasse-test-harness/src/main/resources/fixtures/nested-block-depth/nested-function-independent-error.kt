package sample

fun outer(a: Boolean, b: Boolean, c: Boolean, d: Boolean, e: Boolean) {
    fun inner() {
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
    inner()
}

// expect-error 4:9 nested-block-depth "Function 'inner' is nested too deeply (depth 5); the maximum allowed is 4"
