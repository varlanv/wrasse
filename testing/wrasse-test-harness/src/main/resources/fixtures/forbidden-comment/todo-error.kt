package sample

fun foo() {
    // TODO: fix this
    println("hi")
}

// expect-error 4:5 forbidden-comment "This comment contains 'TODO:', which is forbidden in production code"
