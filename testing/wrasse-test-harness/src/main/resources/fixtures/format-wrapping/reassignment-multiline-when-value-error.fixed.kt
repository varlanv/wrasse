package sample

fun example(x: Int): Int {
    var y = 0
    y =
        when (x) {
            1 -> 10
            else -> 20
        }
    return y
}