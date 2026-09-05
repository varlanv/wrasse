package sample

class Point(val x: Int, val y: Int)

class Box(val corner: Point)

@Suppress("named-arguments")
fun demo(): Box = Box(Point(1, 2))

// expect-clean
