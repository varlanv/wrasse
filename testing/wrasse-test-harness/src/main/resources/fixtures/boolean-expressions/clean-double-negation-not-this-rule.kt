package sample

fun foo(a: Boolean) {
    if (!!a) {
        println("hi")
    }
}

// expect-clean
