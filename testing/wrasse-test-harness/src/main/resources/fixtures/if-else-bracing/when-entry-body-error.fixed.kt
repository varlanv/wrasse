package sample

fun demo(x: Int): Int {
    return when {
        x > 0 ->
            if (true) {
                1
            } else {
                2
            }
        else -> 0
    }
}