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
fun table() = 1

// expect-error 8:1 format-matrix "'// wrasse matrix:2' has no call to lay out: no call with a parenthesized argument list starts on the first line below it that is neither an annotation-only line nor another layout directive (a function, constructor or class declaration is not a call)"
