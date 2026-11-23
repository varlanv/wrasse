package sample

// wrasse matrix:2
val xs = arrayOf(1, 2, 3)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' cannot split the 3 elements of the call below into rows of 2: 3 is not divisible by 2"
