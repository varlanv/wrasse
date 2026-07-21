package sample

fun accept(a: Any?) {}

fun example(x: Boolean) {
    accept(
        if (x) {
            1
        } else {
            2
        },
    )
}

// expect-clean
