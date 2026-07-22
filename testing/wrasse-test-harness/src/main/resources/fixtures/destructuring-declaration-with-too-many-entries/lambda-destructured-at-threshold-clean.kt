package sample

data class Point(val a: Int, val b: Int, val c: Int)

fun use(items: List<Point>) {
    items.forEach { (a, b, c) ->
        println(a + b + c)
    }
}

// expect-clean
