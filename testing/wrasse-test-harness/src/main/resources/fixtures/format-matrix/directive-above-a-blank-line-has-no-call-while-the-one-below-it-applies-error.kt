package sample

// wrasse columns:2

// wrasse matrix:2
val xs = arrayOf(1, 22, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 3:1 format-columns "'// wrasse columns:2' has no call to lay out: no call with a parenthesized argument list starts on the first line below it that is neither an annotation-only line nor another layout directive (a function, constructor or class declaration is not a call)"
