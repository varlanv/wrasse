package sample

fun outer(x: Boolean): Int {
    return run {
        fun inner(): Int = 1
        if (x) return 5
        2
    }
}