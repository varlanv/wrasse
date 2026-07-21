package sample

@Suppress("no-semicolons")
fun foo() {
    // TODO: fix this
    println("hi")
}

// expect-error 5:5 forbidden-comment "This comment contains 'TODO:', which is forbidden in production code"
