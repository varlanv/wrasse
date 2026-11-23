package sample

// wrasse columns:2
fun table() = 1

// wrasse columns:1
val unresolved = unknownFunction(1, 2)

// wrasse columns:7
val xs = arrayOf(1, 2, 3)

// fixture-option: trailing-newline
// expect-clean
