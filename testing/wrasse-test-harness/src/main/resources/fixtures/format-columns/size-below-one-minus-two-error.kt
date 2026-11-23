package sample

// wrasse columns:-2
val xs = arrayOf(1, 2, 3, 4)

// expect-error 3:1 format-columns "size -2 in '// wrasse columns:-2' is below the minimum of 1: a row needs at least 1 element"
