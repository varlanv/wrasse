package sample

// wrasse matrix:2
// wrasse columns:2
val xs = unknownFunction(1, 2, 3, 4)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' (line 3) and '// wrasse columns:2' (line 4) both target the call below; keep only one"
// expect-error 4:1 format-columns "'// wrasse matrix:2' (line 3) and '// wrasse columns:2' (line 4) both target the call below; keep only one"
