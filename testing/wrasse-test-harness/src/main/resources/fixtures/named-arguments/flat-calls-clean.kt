package sample

class Point(val x: Int, val y: Int)

fun origin(): Point = Point(0, 0)

fun items(vararg xs: Int): Int = xs.size

fun demo(): Int {
    val p = Point(1, 2)
    val n = items(1, 2, 3)
    return p.x + p.y + n + origin().x
}

// expect-clean
