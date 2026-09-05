package sample

fun shift(dx: Int = 0, dy: Int = 0): Int = dx + dy

fun demo(): Int = shift(dy = 1)

// expect-clean
