package sample

fun shift(dx: Int = 0, dy: Int = 0, dz: Int = 0): Int = dx + dy + dz

fun wide(a: Int, b: Int, c: Int, d: Int): Int = a + b + c + d

fun demo(): Int = shift(dx = 1, dz = 3) + wide(a = 1, b = 2, c = 3, d = 4)