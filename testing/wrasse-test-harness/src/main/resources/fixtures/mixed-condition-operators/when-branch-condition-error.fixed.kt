package sample

fun check(a: Boolean, b: Boolean, c: Boolean): Int = when {
    (a && b) || c -> 1
    else -> 2
}