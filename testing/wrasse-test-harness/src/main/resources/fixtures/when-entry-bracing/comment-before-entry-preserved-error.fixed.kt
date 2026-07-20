package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        // comment before two
        2 -> {
            "two"
        }
        else -> {
            "other"
        }
    }
}