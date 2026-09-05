package sample

fun shift(dx: Int = 0, dy: Int = 0): Int = dx + dy

fun demo(): Int = shift(dx = 1, dy = 2) + shift(dx = 3)

// expect-error 5:24 named-arguments "Arguments of a callee with fewer than 3 parameters should be positional"
// expect-error 5:48 named-arguments "Arguments of a callee with fewer than 3 parameters should be positional"
