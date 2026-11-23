package sample

@Suppress("unused")
// wrasse matrix:3
// wrasse columns:3
@JvmField
val xs = arrayOf(1, 22, 333, 4, 55, 666)

// expect-error 4:1 format-matrix "'// wrasse matrix:3' (line 4) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
// expect-error 5:1 format-columns "'// wrasse matrix:3' (line 4) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
