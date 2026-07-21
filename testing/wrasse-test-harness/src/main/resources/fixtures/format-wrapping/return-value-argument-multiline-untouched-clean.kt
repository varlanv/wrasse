package sample

fun accept(a: Any?): Any? = a

fun example(x: Boolean): Any? {
    return accept(
        if (x) {
            1
        } else {
            2
        },
    )
}

// expect-clean
