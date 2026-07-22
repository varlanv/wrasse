package sample

@Suppress("no-such-rule")
fun foo(x: Boolean) {
    if (x || true) {
        println("hi")
    }
}

// expect-error 5:9 boolean-expressions "This boolean condition can be simplified"
