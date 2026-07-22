package sample

/**
 * KDoc 1
 */

/**
 * KDoc 2
 */
fun foo() {}

// expect-error 7:1 no-consecutive-comments "a KDoc may not be preceded by a KDoc"
