package sample

class Point(val x: Int, val y: Int)

fun apply(point: Point, block: (Point) -> Int): Int = block(point)

fun demo(): Int {
    val a = Point(x = 1, y = 2)
    val b = Point(3, 4)
    val `weird name` = 1
    return apply(point = a) { it.x } + apply(b) { it.y } + `weird name`
}

// expect-clean
