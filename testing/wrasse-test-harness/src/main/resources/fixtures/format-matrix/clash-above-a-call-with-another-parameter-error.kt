package sample

fun optional(x: Int, vararg xs: Int) = x

// wrasse matrix:2
// wrasse columns:2
val y = optional(1, 2)

// expect-error 5:1 format-matrix "'// wrasse matrix:2' (line 5) and '// wrasse columns:2' (line 6) both target the call below; keep only one"
// expect-error 6:1 format-columns "'// wrasse matrix:2' (line 5) and '// wrasse columns:2' (line 6) both target the call below; keep only one"
