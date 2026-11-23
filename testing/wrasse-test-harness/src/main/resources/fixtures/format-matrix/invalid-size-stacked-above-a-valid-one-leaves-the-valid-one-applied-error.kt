package sample

// wrasse columns:abc
// wrasse matrix:2
val xs = arrayOf(1, 22, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 3:1 format-columns "invalid size 'abc' in '// wrasse columns:abc': the size must be a whole number of at least 1"
