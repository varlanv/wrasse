package sample

fun shift(dx: Int = 0, dy: Int = 0, dz: Int = 0): Int = dx + dy + dz

fun wide(a: Int, b: Int, c: Int, d: Int): Int = a + b + c + d

fun demo(): Int = shift(1, dz = 3) + wide(1, b = 2, c = 3, d = 4)

// expect-error 7:24 named-arguments "Positional arguments should be named"
// expect-error 7:42 named-arguments "Positional arguments should be named"
