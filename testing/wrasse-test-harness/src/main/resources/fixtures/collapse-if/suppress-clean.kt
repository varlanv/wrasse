package sample

@Suppress("collapse-if")
fun foo(a: Boolean, b: Boolean) {
    if (a) {
        if (b) {
            println("both")
        }
    }
}

// expect-clean
