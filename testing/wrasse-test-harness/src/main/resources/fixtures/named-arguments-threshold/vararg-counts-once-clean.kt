package sample

class Point(val x: Int, val y: Int)

fun items(vararg points: Point): Int = points.size

fun first(point: Point): Point = point

fun demo(): Int = items(first(Point(x = 1, y = 2)))

// expect-clean
