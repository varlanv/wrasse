package sample

fun one(x: Int) = x

// wrasse columns:2
val y = one(1)

// expect-error 5:1 format-columns "'// wrasse columns:2' targets a call to 'one', which does not declare exactly one parameter that is a vararg"
