package sample

class Point(val x: Int, val y: Int)

fun shift(point: Point, dx: Int): Point = Point(point.x + dx, point.y)

fun demo(): Point = shift(Point(1, 2), 3)

// expect-error 5:48 named-arguments "Positional arguments should be named"
// expect-error 7:26 named-arguments "Positional arguments should be named"
// expect-error 7:32 named-arguments "Positional arguments should be named"
