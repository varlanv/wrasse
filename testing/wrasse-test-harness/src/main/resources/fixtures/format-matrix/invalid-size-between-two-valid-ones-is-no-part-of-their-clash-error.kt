package sample

// wrasse matrix:2
// wrasse columns:x
// wrasse columns:3
val xs = arrayOf(1, 22, 3, 4)

// expect-error 3:1 format-matrix "'// wrasse matrix:2' (line 3) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
// expect-error 4:1 format-columns "invalid size 'x' in '// wrasse columns:x': the size must be a whole number of at least 2"
// expect-error 5:1 format-columns "'// wrasse matrix:2' (line 3) and '// wrasse columns:3' (line 5) both target the call below; keep only one"
