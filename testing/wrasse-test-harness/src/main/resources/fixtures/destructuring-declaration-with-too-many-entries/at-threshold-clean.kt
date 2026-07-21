package sample

data class Point(val a: Int, val b: Int, val c: Int)

fun use(p: Point) {
    val (a, b, c) = p
}

// expect-clean
