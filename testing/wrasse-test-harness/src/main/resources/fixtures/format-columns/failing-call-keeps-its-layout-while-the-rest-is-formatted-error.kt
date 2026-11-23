package sample

// wrasse columns:0
val xs = intArrayOf(1,2, 3,4,5,
    6)

val y   = 1

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 3:1 format-columns "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
