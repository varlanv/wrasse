package sample

class Point(val x: Int, val y: Int)

fun shift(point: Point, dx: Int, dy: Int): Point = Point(point.x + dx, point.y + dy)

fun demo(): Point {
    val a = Point(x = 1, 2)
    return shift(Point(3, 4), dx = a.x, dy = 2)
}

// expect-error 9:23 named-arguments "Positional arguments should be named"
