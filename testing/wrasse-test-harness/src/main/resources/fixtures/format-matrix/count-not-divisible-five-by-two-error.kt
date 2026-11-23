package sample

// wrasse matrix:2
val xs = arrayOf(1, 2, 3, 4, 5)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' cannot split the 5 elements of the call below into rows of 2: 5 is not divisible by 2"
