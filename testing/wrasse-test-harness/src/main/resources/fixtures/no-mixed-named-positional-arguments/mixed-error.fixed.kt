package sample

class Point(val x: Int, val y: Int)

fun shift(point: Point, dx: Int, dy: Int): Point = Point(point.x + dx, point.y + dy)

fun demo(): Point {
    val a = Point(x = 1, y = 2)
    val b = shift(point = a, dx = 1, dy = 2)
    return shift(b, 1, 2)
}