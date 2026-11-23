package sample

class Vec(val x: Int, val y: Int)

// wrasse columns:2
val xs = arrayOf(Vec(1, /* c */ 2))

// expect-error 5:1 format-columns "'// wrasse columns:2' does not support a comment inside the argument list of the call below"
