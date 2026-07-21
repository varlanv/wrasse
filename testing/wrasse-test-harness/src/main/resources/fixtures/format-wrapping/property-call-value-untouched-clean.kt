package sample

fun accept(a: Any?): Any? = a

fun example(x: Boolean): Any? {
    val result = accept(
        if (x) {
            1
        } else {
            2
        },
    )
    return result
}

// expect-clean
