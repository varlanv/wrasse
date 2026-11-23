package sample

// wrasse columns:-1
val xs = arrayOf(1, 2, 3, 4)

// expect-error 3:1 format-columns "size -1 in '// wrasse columns:-1' is below the minimum of 2: a row needs at least 2 elements"
