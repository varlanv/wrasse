package sample

fun classify(x: Int): String {
    return when {
        x > 0 -> {
            "positive"
        }
        x < 0 -> {
            "negative"
        }
        else -> {
            "zero"
        }
    }
}