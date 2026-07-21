package sample

val foo = 1

fun use() = "${`foo`}"

// expect-error 5:16 unnecessary-backticks "Backticks are unnecessary"
