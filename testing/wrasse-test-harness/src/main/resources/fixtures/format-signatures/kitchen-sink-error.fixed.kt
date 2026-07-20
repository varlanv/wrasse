package sample

fun sum(
    a: Int,
    b: Int,
    c: Int,
): Int {
    return a + b + c
}

fun add(a: Int, b: Int): Int = a + b

fun multiply(a: Int, b: Int): Int {
    return a * b
}

class Point(val x: Int, val y: Int, val z: Int)