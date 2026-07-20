package sample

class Point(val x: Int, val y: Int) {
    fun distanceSquared(other: Point): Int {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }
}

fun demo(points: List<Point>): List<Int> {
    return points.map { point -> point.distanceSquared(points[0]) }
}

// expect-clean
