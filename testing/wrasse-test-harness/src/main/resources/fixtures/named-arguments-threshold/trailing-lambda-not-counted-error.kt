package sample

class Point(val x: Int, val y: Int)

fun log(throwable: Throwable?, message: () -> String): String = message() + throwable

fun apply(point: Point, block: (Point) -> Int): Int = block(point)

fun demo(e: Throwable): String = log(throwable = e) { "failed" } + apply(Point(1, 2)) { it.x } + log(e) { "again" }

// expect-error 9:37 named-arguments "Arguments of a callee with fewer than 2 parameters should be positional"
// expect-error 9:79 named-arguments "Positional arguments should be named"
