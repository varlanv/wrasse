package sample

class Point(val x: Int, val y: Int)

fun shift(point: Point, dx: Int): Point = Point(x = point.x + dx, y = point.y)

fun demo(): Point = shift(point = Point(x = 1, y = 2), dx = 3)