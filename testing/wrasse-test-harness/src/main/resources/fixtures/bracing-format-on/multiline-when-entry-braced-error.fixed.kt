package sample

fun demo(x: Int): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> {
            "two".plus("!")
        }
        else -> {
            "other"
        }
    }
}