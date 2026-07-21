package sample

fun example(x: Int, cond: Boolean): Int {
    return when (x) {
        1 ->
            if (cond) {
                10
            } else {
                20
            }
        else -> 0
    }
}