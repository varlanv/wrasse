package sample

// wrasse matrix:3
// wrasse columns:2
// wrasse matrix:2
val xs = arrayOf(1, 22, 333, 4, 55, 666)

// expect-error 3:1 format-matrix "'// wrasse matrix:3' (line 3), '// wrasse columns:2' (line 4) and '// wrasse matrix:2' (line 5) all target the call below; keep only one"
// expect-error 4:1 format-columns "'// wrasse matrix:3' (line 3), '// wrasse columns:2' (line 4) and '// wrasse matrix:2' (line 5) all target the call below; keep only one"
// expect-error 5:1 format-matrix "'// wrasse matrix:3' (line 3), '// wrasse columns:2' (line 4) and '// wrasse matrix:2' (line 5) all target the call below; keep only one"
