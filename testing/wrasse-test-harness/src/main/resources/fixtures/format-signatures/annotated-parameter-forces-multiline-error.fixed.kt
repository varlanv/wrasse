package sample

annotation class Foo

fun f(
    @Foo a: Int,
    b: Int,
    c: Int,
): Int = a + b + c