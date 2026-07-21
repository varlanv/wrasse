package sample

fun foo() {}
/**
 * KDoc for bar.
 */
fun bar() {}

// expect-error 1:1 format "File is not wrasse-formatted"
