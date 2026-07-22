package sample

fun foo(a: Boolean, b: Boolean) {
    if (a) {
        if (b) {
            println("both")
        }
    } else {
        println("not a")
    }
}

// expect-clean
