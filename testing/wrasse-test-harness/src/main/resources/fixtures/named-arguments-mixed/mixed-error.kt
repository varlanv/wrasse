package sample

class Point(val x: Int, val y: Int)

fun shift(point: Point, dx: Int, dy: Int): Point = Point(point.x + dx, point.y + dy)

fun demo(): Point {
    val a = Point(x = 1, 2)
    val b = shift(a, dx = 1, dy = 2)
    return shift(b, 1, 2)
}

// expect-error 8:18 named-arguments "Positional arguments should be named"
// expect-error 9:18 named-arguments "Positional arguments should be named"
