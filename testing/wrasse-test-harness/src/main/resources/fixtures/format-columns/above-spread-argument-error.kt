package sample

val base = intArrayOf(1, 2)

// wrasse columns:2
val t = intArrayOf(*base, 3)

// expect-error 5:1 format-columns "'// wrasse columns:2' does not support a spread argument in the call below"
