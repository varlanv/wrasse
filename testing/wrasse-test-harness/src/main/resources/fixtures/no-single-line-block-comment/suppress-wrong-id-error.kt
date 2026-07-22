package sample

@Suppress("no-semicolons")
val x = 1 /* trailing */

// expect-error 4:11 no-single-line-block-comment "Replace the block comment with an EOL comment"
