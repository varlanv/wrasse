package sample

class Point(val x: Int, val y: Int)

class Box(val corner: Point, val width: Int, val height: Int)

fun demo(): Box = Box(corner = Point(1, 2), width = 3, height = 4)