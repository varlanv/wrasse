package sample

fun f(x: Int) = x

val a = 1
val values = listOf(a + 1, f(1), f(2), { a }, a.hashCode().toString(), a)

// expect-error 1:1 format "File is not wrasse-formatted"
