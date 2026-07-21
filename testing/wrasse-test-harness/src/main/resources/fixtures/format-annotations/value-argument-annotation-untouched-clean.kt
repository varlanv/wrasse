package sample

annotation class Ann(val v: String)

fun call(x: Int) = x

val y = call(@Ann("x") 1)

// expect-clean
