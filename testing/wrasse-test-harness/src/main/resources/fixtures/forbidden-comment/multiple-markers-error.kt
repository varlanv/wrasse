package sample

fun foo() {
    // TODO: first
    // FIXME: second
    println("hi")
}

// expect-error 4:5 forbidden-comment "This comment contains 'TODO:', which is forbidden in production code"
// expect-error 5:5 forbidden-comment "This comment contains 'FIXME:', which is forbidden in production code"
