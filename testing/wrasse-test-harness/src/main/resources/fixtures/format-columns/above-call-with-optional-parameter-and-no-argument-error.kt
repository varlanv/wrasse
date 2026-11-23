package sample

fun optional(x: Int = 1) = x

// wrasse columns:2
val y = optional()

// expect-error 5:1 format-columns "'// wrasse columns:2' targets a call to 'optional', which does not declare exactly one parameter that is a vararg"
