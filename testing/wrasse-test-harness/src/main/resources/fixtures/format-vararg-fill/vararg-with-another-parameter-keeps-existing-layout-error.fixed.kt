package sample

fun f(a: Int, vararg v: Int) = a

val sum = f(
    1,
    100,
    200,
    300,
    400,
    500,
    600,
    700,
    800,
    900,
)