package sample

fun foo(a: Boolean, b: Boolean) {
    if (a) {
        println("checking b")
        if (b) {
            println("both")
        }
    }
}

// expect-clean
