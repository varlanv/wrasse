package sample

// wrasse columns:2x
val xs = arrayOf(1, 2, 3, 4)

// expect-error 3:1 format-columns "invalid size '2x' in '// wrasse columns:2x': the size must be a whole number of at least 1"
