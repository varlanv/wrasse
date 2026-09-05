package sample

class Bar(val openTime: Long)

fun pick(
    fallback: Boolean,
    primary: List<Bar>?,
    secondary: List<Bar>?,
): List<Bar> {
    val seconds = (if (fallback) {
        primary
    } else {
        secondary
    })?.sortedBy { it.openTime }?.distinctBy { it.openTime }.orEmpty()
    return seconds
}