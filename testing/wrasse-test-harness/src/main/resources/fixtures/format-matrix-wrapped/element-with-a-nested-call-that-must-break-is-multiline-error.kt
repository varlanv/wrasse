package sample

class Vec(val x: Int, val y: Int)

class Pair2(val a: Vec, val b: Int)

// wrasse matrix:2
val points = listOf(
    Pair2(
        Vec(1, 2),
        3,
    ),
    Pair2(
        Vec(4, 5),
        6,
    ),
)

// expect-error 7:1 format-matrix "'// wrasse matrix:2' needs every element on one line, but an element of the call below must stay on several lines"
