package sample

@Suppress("boolean-expressions")
fun foo(x: Boolean) {
    if (x || true) {
        println("hi")
    }
}

// expect-clean
