package sample

class Vec(val x: Int, val y: Int)

// wrasse matrix:2
val points = listOf(
    Vec(
        1,
        2,
    ), Vec(3, 4),
    Vec(5,
        6), Vec(7, 8),
)

// expect-error 1:1 format "File is not wrasse-formatted"
