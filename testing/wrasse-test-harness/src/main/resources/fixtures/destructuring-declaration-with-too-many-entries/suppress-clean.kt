package sample

data class Point(val a: Int, val b: Int, val c: Int, val d: Int)

@Suppress("destructuring-declaration-with-too-many-entries")
fun use(p: Point) {
    val (a, b, c, d) = p
}

// expect-clean
