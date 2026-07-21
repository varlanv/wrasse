package sample

data class Point(val a: Int, val b: Int, val c: Int, val d: Int)

fun use(p: Point) {
    val (a, b, c, d) = p
}

// expect-error 6:5 destructuring-declaration-with-too-many-entries "Destructuring declaration has 4 entries; the maximum allowed is 3"
