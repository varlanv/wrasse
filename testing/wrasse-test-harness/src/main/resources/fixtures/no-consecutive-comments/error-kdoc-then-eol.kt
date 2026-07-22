package sample

/**
 * KDoc 1
 */

// eol comment
fun foo() {}

// expect-error 7:1 no-consecutive-comments "an EOL comment may not be preceded by a KDoc. Reversed order is allowed though when separated by a newline."
