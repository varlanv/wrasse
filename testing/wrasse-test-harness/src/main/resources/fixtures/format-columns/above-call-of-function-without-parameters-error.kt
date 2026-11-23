package sample

fun none() = 1

// wrasse columns:2
val y = none()

// expect-error 5:1 format-columns "'// wrasse columns:2' targets a call to 'none', which does not declare exactly one parameter that is a vararg"
