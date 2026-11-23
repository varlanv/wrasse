package sample

// wrasse fmt:off
val   y = 1
// wrasse fmt:on

// wrasse matrix:2
// wrasse columns:3
val xs = arrayOf(1, 22, 3, 4)

// expect-error 7:1 format-matrix "'// wrasse matrix:2' (line 7) and '// wrasse columns:3' (line 8) both target the call below; keep only one"
// expect-error 8:1 format-columns "'// wrasse matrix:2' (line 7) and '// wrasse columns:3' (line 8) both target the call below; keep only one"
