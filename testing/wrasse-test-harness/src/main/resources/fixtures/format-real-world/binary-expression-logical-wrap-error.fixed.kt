package sample

fun isTickerShapedRegion(
    s: String,
    start: Int,
    end: Int,
): Boolean = s.substring(start, end).all { it.isUpperCase() }

fun findTicker(s: String): String? {
    val contentStart = 5
    val contentEnd = 10
    if (contentStart < contentEnd &&
        contentEnd - contentStart <= 20 &&
        isTickerShapedRegion(s, contentStart, contentEnd)) {
        return s.substring(contentStart, contentEnd)
    }
    return null
}