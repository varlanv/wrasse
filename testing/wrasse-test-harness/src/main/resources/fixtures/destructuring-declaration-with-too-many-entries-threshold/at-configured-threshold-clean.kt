package sample

data class Point(val a: Int, val b: Int)

fun use(p: Point) {
    val (a, b) = p
}

// expect-clean
