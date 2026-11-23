package sample

// wrasse matrix:3
// wrasse columns:3
val xs = arrayOf(1,2, 3,4,5,
    6)

val y   = 1

// expect-error 3:1 format-matrix "'// wrasse matrix:3' (line 3) and '// wrasse columns:3' (line 4) both target the call below; keep only one"
// expect-error 4:1 format-columns "'// wrasse matrix:3' (line 3) and '// wrasse columns:3' (line 4) both target the call below; keep only one"
// expect-error 1:1 format "File is not wrasse-formatted"
