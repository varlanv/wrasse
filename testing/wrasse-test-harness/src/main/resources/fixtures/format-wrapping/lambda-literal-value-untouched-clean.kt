package sample

fun example(cond: Boolean) {
    val f = {
        if (cond) {
            1
        } else {
            2
        }
    }
    f()
}

// expect-clean
