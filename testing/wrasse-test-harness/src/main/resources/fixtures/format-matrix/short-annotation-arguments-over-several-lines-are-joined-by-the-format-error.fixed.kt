package sample

annotation class Marker(
    val a: Int,
    val b: IntArray,
)

// wrasse matrix:2
@Marker(a = 1, b = [1, 2])
val xs = intArrayOf(
    1, 22,
    3, 4,
)