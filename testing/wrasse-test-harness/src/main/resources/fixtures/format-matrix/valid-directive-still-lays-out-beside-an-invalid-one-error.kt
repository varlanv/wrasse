package sample

// wrasse matrix:2
val xs = arrayOf(1, 2, 3, 4)

// wrasse matrix:3
val ys = arrayOf(1,2, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
// expect-error 6:1 format-matrix "'// wrasse matrix:3' cannot split the 4 elements of the call below into rows of 3: 4 is not divisible by 3"
