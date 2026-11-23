package sample

// wrasse matrix:1
// wrasse columns:0
val xs = arrayOf(1,  22, 3)

// expect-error 3:1 format-matrix "size 1 in '// wrasse matrix:1' is below the minimum of 2: a row needs at least 2 elements"
// expect-error 4:1 format-columns "size 0 in '// wrasse columns:0' is below the minimum of 1: a row needs at least 1 element"
