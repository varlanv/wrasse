package sample

fun id(vararg xs: Int) = xs

// wrasse columns:2
val t = id(1, 2, 3, 4).size + id(5, 6, 7, 8).size

// expect-error 1:1 format "File is not wrasse-formatted"
