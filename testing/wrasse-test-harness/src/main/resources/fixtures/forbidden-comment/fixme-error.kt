package sample

fun foo() {
    /* FIXME: hack */
    println("hi")
}

// expect-error 4:5 forbidden-comment "This comment contains 'FIXME:', which is forbidden in production code"
