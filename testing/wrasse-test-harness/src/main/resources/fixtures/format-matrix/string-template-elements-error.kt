package sample

val a = "x"
val b = 2

// wrasse matrix:2
val ts = listOf("a$a", "b${b + 1}", "plain", "${a}${b}")

// expect-error 1:1 format "File is not wrasse-formatted"
