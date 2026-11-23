package sample

annotation class Marker(
    val a: Int,
    val b: IntArray,
)

// wrasse columns:2
@Marker(a = 1, b = [1, 2])
val xs = intArrayOf(1, 22, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
