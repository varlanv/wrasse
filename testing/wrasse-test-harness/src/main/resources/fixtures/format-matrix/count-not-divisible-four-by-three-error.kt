package sample

// wrasse matrix:3
val xs = arrayOf(1, 2, 3, 4)

// expect-error 3:1 format-matrix "'// wrasse matrix:3' cannot split the 4 elements of the call below into rows of 3: 4 is not divisible by 3"
