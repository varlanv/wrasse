package sample

class Point(val x: Int, val y: Int)

fun shift(point: Point, dx: Int, dy: Int): Point = Point(point.x + dx, point.y + dy)

fun demo(): Point = shift(Point(1, 2), dx = 1, dy = 2)

// expect-error 7:26 named-arguments "Positional arguments should be named"
// expect-error 7:26 no-mixed-named-positional-arguments "Named and positional arguments must not be mixed in one call"
// expect-error 7:32 named-arguments "Positional arguments should be named"
