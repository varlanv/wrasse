package sample

fun shift(dx: Int = 0, dy: Int = 0): Int = dx + dy

fun demo(): Int = shift(1, dy = 2)

// expect-error 5:24 named-arguments "Arguments of a callee with fewer than 3 parameters should be positional"
