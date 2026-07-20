package sample

fun classify(x: Int): String {
    return when (x) {
        1 ->
            if (x > 10) {
                "big"
            } else {
                "small"
            }
        2 -> {
            println("computing")
            "two"
        }
        else -> {
            "other"
        }
    }
}