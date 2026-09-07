package sample

class Point(val x: Int, val y: Int)

fun log(throwable: Throwable?, message: () -> String): String = message() + throwable

fun apply(point: Point, block: (Point) -> Int): Int = block(point)

fun demo(e: Throwable): String = log(e) { "failed" } + apply(Point(x = 1, y = 2)) { it.x } + log(e) { "again" }