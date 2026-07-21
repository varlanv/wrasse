package sample

val foo = 1

fun use() = "$`foo`bar"

// expect-error 5:15 unnecessary-backticks "Backticks are unnecessary (no autofix for this shape)"
