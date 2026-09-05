package sample

class Point(val first: Long, val second: Double)

class Windows(val endMillis: Long, val hourMillis: Long) {
    fun latest(points: List<Point>): Point? {
        val oiNow = points.lastOrNull { it.first <= endMillis && it.first > endMillis - 2L * hourMillis && it.second > 0.0 && it.second < 1000000.0 && it.second != 42.0 }
        while (endMillis < points.size && points.size < 100 && points.first().first - points.last().first < endMillis * 1000L * 1000L) {
            return null
        }
        return oiNow
    }
}

// expect-error 1:1 format "File is not wrasse-formatted"
