package sample

fun foo() {}
/*
 * block comment
 */
fun bar() {}

// expect-error 1:1 format "File is not wrasse-formatted"
