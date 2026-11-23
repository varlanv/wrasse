package sample

// wrasse matrix:3
// wrasse columns:3
val xs = arrayOf(1, 22, 333, 4, 55, 666)

// fixture-option: warn-only
// expect-warning 3:1 format-matrix "'// wrasse matrix:3' (line 3) and '// wrasse columns:3' (line 4) both target the call below; keep only one"
// expect-warning 4:1 format-columns "'// wrasse matrix:3' (line 3) and '// wrasse columns:3' (line 4) both target the call below; keep only one"
