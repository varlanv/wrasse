package sample

// wrasse columns:1
@Suppress("unused")
// wrasse matrix:2
internal val xs = arrayOf(1, 22, 3, 4)

// expect-error 3:1 format-columns "'// wrasse columns:1' (line 3) and '// wrasse matrix:2' (line 5) both target the call below; keep only one"
// expect-error 5:1 format-matrix "'// wrasse columns:1' (line 3) and '// wrasse matrix:2' (line 5) both target the call below; keep only one"
