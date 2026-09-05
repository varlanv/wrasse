package sample

class Point(val first: Long, val second: Double)

class Windows(val endMillis: Long, val hourMillis: Long) {
    fun latest(points: List<Point>): Point? {
        val oiNow = points.lastOrNull {
            it.first <= endMillis && it.first > endMillis - 2L * hourMillis && it.second > 0.0 && it.second < 1000000.0
        }
        return oiNow
    }
}