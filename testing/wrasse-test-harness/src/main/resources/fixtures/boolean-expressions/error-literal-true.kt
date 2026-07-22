package sample

fun foo(x: Boolean) {
    if (x || true) {
        println("hi")
    }
}

// expect-error 4:9 boolean-expressions "This boolean condition can be simplified"
