package sample

fun Int.rangeTo(a: Int, b: Int): Int = a

val x = 5
val r = x.rangeTo(1, 2)

// expect-clean
