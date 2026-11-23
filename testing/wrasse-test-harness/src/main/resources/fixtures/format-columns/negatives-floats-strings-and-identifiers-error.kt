package sample

val name = "n"
val other = 7
val x = 9

// wrasse columns:3
val items = listOf<Any>(-1, 0.5f, "ab", name, -250, 'c', other, 1_000, x)

// expect-error 1:1 format "File is not wrasse-formatted"
