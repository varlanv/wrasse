package sample

fun id(vararg xs: Any) = xs

// wrasse columns:2
val t = id(id(1, 2), 3, id(4, 5), 6)

// expect-error 1:1 format "File is not wrasse-formatted"
