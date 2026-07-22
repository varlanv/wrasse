package sample

fun foo(x: Boolean) {
    if (x) {
        println("work")
    } else {
        // no-op
    }
}

// expect-clean
