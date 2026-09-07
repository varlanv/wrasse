package sample

fun check(a: Boolean, b: Boolean, c: Boolean, d: Boolean, e: Boolean): Boolean {
    return a && (b || (c && d)) && e
}