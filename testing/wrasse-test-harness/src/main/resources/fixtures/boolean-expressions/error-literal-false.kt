package sample

fun foo(x: Boolean) {
    if (x && false) {
        println("hi")
    }
}

// expect-error 4:9 boolean-expressions "This boolean condition can be simplified"
