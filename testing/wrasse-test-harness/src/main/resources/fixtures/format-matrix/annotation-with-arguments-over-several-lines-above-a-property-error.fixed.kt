package sample

annotation class Marker(
    val a: Int,
    val b: IntArray,
)

// wrasse matrix:2
@Marker(
    a = 111111,
    b = [1111111, 2222222, 3333333],
)
val xs = intArrayOf(
    1, 22,
    3, 4,
)