package sample

// wrasse columns:2
val xs = arrayOf(1, 2, /* three */ 3, 4)

// expect-error 3:1 format-columns "'// wrasse columns:2' does not support a comment inside the argument list of the call below"
