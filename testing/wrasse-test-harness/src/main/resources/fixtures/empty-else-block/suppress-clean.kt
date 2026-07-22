package sample

@Suppress("empty-else-block")
fun foo(x: Boolean) {
    if (x) {
        println("work")
    } else {
    }
}

// expect-clean
