package sample

// wrasse matrix:2
// wrasse columns:2
fun table() = 1

// expect-error 3:1 format-matrix "'// wrasse matrix:2' has no call to lay out: no call with a parenthesized argument list starts on the first line below it that is neither an annotation-only line nor another layout directive (a function, constructor or class declaration is not a call)"
// expect-error 4:1 format-columns "'// wrasse columns:2' has no call to lay out: no call with a parenthesized argument list starts on the first line below it that is neither an annotation-only line nor another layout directive (a function, constructor or class declaration is not a call)"
