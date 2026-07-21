package sample

/**
 * STOPSHIP: remove before release
 */
fun foo() {
    println("hi")
}

// expect-error 3:1 forbidden-comment "This comment contains 'STOPSHIP:', which is forbidden in production code"
