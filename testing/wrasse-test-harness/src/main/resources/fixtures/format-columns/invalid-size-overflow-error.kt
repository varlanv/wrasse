package sample

// wrasse columns:99999999999
val xs = arrayOf(1, 2, 3, 4)

// expect-error 3:1 format-columns "invalid size '99999999999' in '// wrasse columns:99999999999': the size must be a whole number of at least 2"
