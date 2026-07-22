package sample

/**
 * @deprecated old
 */
@Suppress("no-semicolons")
fun foo() {
    println("hi")
}

// expect-error 3:1 kdoc-deprecated-tag "The @deprecated tag block does not properly report deprecation in Kotlin, use @Deprecated annotation instead"
