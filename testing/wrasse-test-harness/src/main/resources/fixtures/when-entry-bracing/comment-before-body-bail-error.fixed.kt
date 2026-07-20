package sample

fun classify(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 ->
            // comment
            "two"
        else -> {
            "other"
        }
    }
}