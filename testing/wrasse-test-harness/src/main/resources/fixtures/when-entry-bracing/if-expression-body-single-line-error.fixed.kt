package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> {
            if (x > 0) "pos" else "neg"
        }
        else -> {
            "other"
        }
    }
}