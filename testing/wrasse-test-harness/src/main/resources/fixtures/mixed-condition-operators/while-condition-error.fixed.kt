package sample

fun check(a: Boolean, b: Boolean, c: Boolean) {
    while ((a && b) || c) {
        break
    }
}