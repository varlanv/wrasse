package sample

// wrasse matrix:4
val xs = intArrayOf(1,2, 3,4,5,
    6)

val y   = 1

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 3:1 format-matrix "'// wrasse matrix:4' cannot split the 6 elements of the call below into rows of 4: 6 is not divisible by 4"
