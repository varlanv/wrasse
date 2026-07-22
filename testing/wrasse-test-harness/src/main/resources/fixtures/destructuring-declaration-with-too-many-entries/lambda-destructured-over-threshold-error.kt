package sample

data class Point(val a: Int, val b: Int, val c: Int, val d: Int)

fun use(items: List<Point>) {
    items.forEach { (a, b, c, d) ->
        println(a + b + c + d)
    }
}

// expect-error 6:21 destructuring-declaration-with-too-many-entries "Destructuring declaration has 4 entries; the maximum allowed is 3"
