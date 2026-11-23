package sample

fun table(p: String, vararg xs: Int) = 1

// wrasse columns:2
val t = table("a", 1, 2)

// expect-error 5:1 format-columns "'// wrasse columns:2' targets a call to 'table', which does not declare exactly one parameter that is a vararg"
