package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> {
            "two"
        }
        3 -> "three"
        else -> "other"
    }
}