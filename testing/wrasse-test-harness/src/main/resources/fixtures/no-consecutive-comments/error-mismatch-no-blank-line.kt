package sample

/*
 * Block 1
 */
// eol comment
fun foo() {}

// expect-error 6:1 no-consecutive-comments "an EOL comment may not be preceded by a block comment unless separated by a blank line"
