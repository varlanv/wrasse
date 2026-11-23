package sample

fun one(x: Int) = x

// wrasse matrix:2
val y = one(1)

// expect-error 5:1 format-matrix "'// wrasse matrix:2' targets a call to 'one', which does not declare exactly one parameter that is a vararg"
