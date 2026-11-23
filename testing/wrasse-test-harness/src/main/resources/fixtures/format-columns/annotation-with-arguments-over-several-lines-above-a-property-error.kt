package sample

annotation class Marker(
    val a: Int,
    val b: IntArray,
)

// wrasse columns:2
@Marker(
    a = 111111,
    b = [1111111, 2222222, 3333333],
)
val xs = intArrayOf(1, 22, 3, 4)

// expect-error 1:1 format "File is not wrasse-formatted"
