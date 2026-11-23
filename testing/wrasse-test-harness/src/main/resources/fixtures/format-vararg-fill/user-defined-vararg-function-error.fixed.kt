package sample

fun f(vararg v: Int): Int = v.sum()

val sum = f(
    100, 200, 300, 400, 500, 600, 700,
    800, 900,
)