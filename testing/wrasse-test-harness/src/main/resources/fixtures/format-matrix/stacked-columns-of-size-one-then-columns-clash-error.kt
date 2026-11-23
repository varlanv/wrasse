package sample

// wrasse columns:1
// wrasse columns:2
val xs = arrayOf(1, 22, 3, 4)

// expect-error 3:1 format-columns "'// wrasse columns:1' (line 3) and '// wrasse columns:2' (line 4) both target the call below; keep only one"
// expect-error 4:1 format-columns "'// wrasse columns:1' (line 3) and '// wrasse columns:2' (line 4) both target the call below; keep only one"
