package sample

class Point(val x: Int, val y: Int)

fun wrap(value: Point): Point = value

fun demo(): Point = wrap(/* c */ value = Point(x = 1, y = 2))

// expect-clean
