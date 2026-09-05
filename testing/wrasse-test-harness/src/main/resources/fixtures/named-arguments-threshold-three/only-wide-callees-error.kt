package sample

class Point(val x: Int, val y: Int)

class Box(val corner: Point, val width: Int, val height: Int)

fun demo(): Box = Box(Point(1, 2), 3, 4)

// expect-error 7:22 named-arguments "Positional arguments should be named"
