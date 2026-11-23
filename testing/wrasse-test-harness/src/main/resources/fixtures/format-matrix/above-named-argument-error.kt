package sample

fun table(vararg xs: Int) = xs.size

// wrasse matrix:2
val t = table(xs = *intArrayOf(1, 2))

// expect-error 5:1 format-matrix "'// wrasse matrix:2' does not support a named argument in the call below"
