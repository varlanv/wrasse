package sample

// wrasse matrix:abc
// wrasse columns:1
val xs = arrayOf(1,  22, 3, 4)

// expect-error 3:1 format-matrix "invalid size 'abc' in '// wrasse matrix:abc': the size must be a whole number of at least 2"
// expect-error 4:1 format-columns "size 1 in '// wrasse columns:1' is below the minimum of 2: a row needs at least 2 elements"
