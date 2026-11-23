package sample

class Holder {
    // wrasse matrix:2
    // wrasse columns:2
    val xs = intArrayOf(1, 2, 3, 4)
}

// expect-error 4:5 format-matrix "'// wrasse matrix:2' (line 4) and '// wrasse columns:2' (line 5) both target the call below; keep only one"
// expect-error 5:5 format-columns "'// wrasse matrix:2' (line 4) and '// wrasse columns:2' (line 5) both target the call below; keep only one"
