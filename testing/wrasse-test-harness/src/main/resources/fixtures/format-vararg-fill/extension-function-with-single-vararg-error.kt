package sample

fun Int.f(vararg v: Int): Int = v.sum()

val sum = 1.f(100, 200, 300, 400, 500, 600, 700, 800, 900)

// expect-error 1:1 format "File is not wrasse-formatted"
