package sample

/**
 * KDoc 1
 */
/*
 * Block 1
 */
fun foo() {}

// expect-error 6:1 no-consecutive-comments "a block comment may not be preceded by a KDoc. Reversed order is allowed though when separated by a newline."
