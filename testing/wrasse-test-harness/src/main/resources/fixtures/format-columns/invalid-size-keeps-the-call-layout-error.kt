package sample

// wrasse columns:abc
val xs = intArrayOf(1,2, 3,4,5,
    6)

// expect-error 3:1 format-columns "invalid size 'abc' in '// wrasse columns:abc': the size must be a whole number of at least 1"
