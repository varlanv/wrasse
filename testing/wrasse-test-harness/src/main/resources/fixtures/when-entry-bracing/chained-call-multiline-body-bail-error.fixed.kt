package sample

fun classify(x: String): String {
    return when (x) {
        "a" -> {
            "one"
        }
        "b" -> x
            .plus("!")
            .plus("?")
        else -> {
            "other"
        }
    }
}