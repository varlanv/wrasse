package sample

fun foo(a: Boolean) {
    if (a && !a) {
        println("hi")
    }
}

// expect-error 4:9 boolean-expressions "This boolean condition can be simplified"
