package sample

fun optional(x: Int = 1) = x

// wrasse matrix:2
val y = optional()

// expect-error 5:1 format-matrix "'// wrasse matrix:2' targets a call to 'optional', which does not declare exactly one parameter that is a vararg"
