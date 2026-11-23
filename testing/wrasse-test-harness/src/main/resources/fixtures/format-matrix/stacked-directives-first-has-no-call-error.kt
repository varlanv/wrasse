package sample

// wrasse matrix:2
// wrasse matrix:3
val xs = arrayOf(1, 2, 3, 4, 5, 6)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 3:1 format-matrix "'// wrasse matrix:2' has no call to lay out: no call with a parenthesized argument list starts on the line directly below it (a function, constructor or class declaration is not a call)"
