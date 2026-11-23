package sample

fun none() = 1

// wrasse matrix:2
val y = none()

// expect-error 5:1 format-matrix "'// wrasse matrix:2' targets a call to 'none', which does not declare exactly one parameter that is a vararg"
