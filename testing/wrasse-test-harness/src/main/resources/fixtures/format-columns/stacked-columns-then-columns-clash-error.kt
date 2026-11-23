package sample

// wrasse columns:2
// wrasse columns:3
val xs = arrayOf(1, 22, 333, 4, 55, 666)

// expect-error 3:1 format-columns "'// wrasse columns:2' (line 3) and '// wrasse columns:3' (line 4) both target the call below; keep only one"
// expect-error 4:1 format-columns "'// wrasse columns:2' (line 3) and '// wrasse columns:3' (line 4) both target the call below; keep only one"
