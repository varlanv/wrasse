package sample

fun add(a: Int, b: Int): Int = a + b

fun sum(
    a: Int,
    b: Int,
    c: Int,
): Int {
    return a + b + c
}

fun greet() {
    println("hi")
}

class Point(
    val x: Int,
    val y: Int,
    val z: Int,
)

// expect-clean
