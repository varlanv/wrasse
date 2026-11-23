package sample

fun f(vararg v: Int, g: (Int) -> Int) = g(1)

val sum = f(100, 200, 300, 400, 500, 600, 700, 800) { it }

// expect-error 1:1 format "File is not wrasse-formatted"
