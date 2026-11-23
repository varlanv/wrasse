package sample

val xs = arrayOf(1, 2, 3, 4)

// wrasse matrix:2

// expect-error 5:1 format-matrix "'// wrasse matrix:2' has no call to lay out: no call with a parenthesized argument list starts on the first line below it that is neither an annotation-only line nor another layout directive (a function, constructor or class declaration is not a call)"
