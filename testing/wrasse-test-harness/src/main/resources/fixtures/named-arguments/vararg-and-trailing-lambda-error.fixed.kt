package sample

class Point(val x: Int, val y: Int)

fun items(vararg points: Point): Int = points.size

fun apply(point: Point, block: (Point) -> Int): Int = block(point)

fun demo(): Int {
    val fromVararg = items(Point(x = 1, y = 2), Point(x = 3, y = 4))
    val fromLambda = apply(point = Point(x = 1, y = 2)) { it.x }
    return fromVararg + fromLambda
}