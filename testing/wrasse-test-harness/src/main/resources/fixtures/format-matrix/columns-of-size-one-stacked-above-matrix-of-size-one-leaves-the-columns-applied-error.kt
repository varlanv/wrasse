package sample

// wrasse columns:1
// wrasse matrix:1
val xs = arrayOf(1, 22, 3)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 4:1 format-matrix "size 1 in '// wrasse matrix:1' is below the minimum of 2: a row needs at least 2 elements"
