package sample

class Point(val x: Int, val y: Int)

class Box(val corner: Point, val label: String)

fun demo(): Box = Box(corner = Point(x = 1, y = 2), label = "a")

// expect-clean
