package sample

// wrasse columns:2
val xs = arrayOf(1, 2, 3, 4)

// wrasse columns:0
val ys = arrayOf(1,2, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 6:1 format-columns "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
