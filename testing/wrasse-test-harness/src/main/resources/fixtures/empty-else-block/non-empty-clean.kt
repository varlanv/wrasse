package sample

fun foo(x: Boolean) {
    if (x) {
        println("work")
    } else {
        println("fallback")
    }
}

// expect-clean
