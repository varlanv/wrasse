package sample

val a = "x"
val b = 2

// wrasse columns:2
val ts = listOf(
    "a$a",   "b${b + 1}",
    "plain", "${a}${b}",
)