package sample

fun f(a: Int, b: Boolean, c: Boolean, d: Boolean, e: Boolean): String {
    if (a == 1) {
        return "one"
    } else if (a == 2) {
        if (b) {
            if (c) {
                if (d) {
                    if (e) {
                        return "deep"
                    }
                }
            }
        }
        return "two"
    } else {
        return "other"
    }
}

// expect-error 3:5 nested-block-depth "Function 'f' is nested too deeply (depth 5); the maximum allowed is 4"
