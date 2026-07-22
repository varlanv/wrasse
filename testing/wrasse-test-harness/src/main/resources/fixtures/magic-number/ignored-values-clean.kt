package sample

fun foo(x: Int) {
}

fun run() {
    foo(-1)
    foo(0)
    foo(1)
    foo(2)
}

// expect-clean
