package sample

fun classify(x: Int): String {
    return when (x) {
        1,
        2 -> {
            "small"
        }
        3 -> {
            "three"
        }
        else -> {
            "other"
        }
    }
}