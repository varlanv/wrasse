package sample

// wrasse matrix:2
@Suppress("unused")
// wrasse columns:3
internal val xs = arrayOf(1, 22, 3, 4)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' (line 3) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
// expect-error 5:1 format-columns "'// wrasse matrix:2' (line 3) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
