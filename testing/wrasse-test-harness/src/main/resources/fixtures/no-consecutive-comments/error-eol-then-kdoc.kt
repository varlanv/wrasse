package sample

// eol comment
/**
 * KDoc
 */
fun foo() {}

// expect-error 4:1 no-consecutive-comments "a KDoc may not be preceded by an EOL comment unless separated by a blank line"
