package sample

fun foo(bar: Int) {
    if (bar < 1) {
        throw IllegalArgumentException("bar must be greater than zero")
    }
}

// expect-clean
