package sample

class Point(val x: Int, val y: Int)

fun items(vararg points: Point): Int = points.size

fun apply(point: Point, block: (Point) -> Int): Int = block(point)

fun demo(): Int {
    val fromVararg = items(Point(1, 2), Point(3, 4))
    val fromLambda = apply(Point(1, 2)) { it.x }
    return fromVararg + fromLambda
}

// expect-error 10:33 named-arguments "Positional arguments should be named"
// expect-error 10:46 named-arguments "Positional arguments should be named"
// expect-error 11:27 named-arguments "Positional arguments should be named"
// expect-error 11:33 named-arguments "Positional arguments should be named"
