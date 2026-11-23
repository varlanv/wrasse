package sample

// wrasse columns:2
@Suppress("unused")
@JvmField
// wrasse matrix:2
internal val xs = arrayOf(1, 22, 3, 4)

// expect-error 3:1 format-columns "'// wrasse columns:2' (line 3) and '// wrasse matrix:2' (line 6) both target the call below; keep only one"
// expect-error 6:1 format-matrix "'// wrasse columns:2' (line 3) and '// wrasse matrix:2' (line 6) both target the call below; keep only one"
