package sample

fun kind(x: Int): String {
    return when (x) {
        1, 2 -> "small"
        else -> "large"
    }
}