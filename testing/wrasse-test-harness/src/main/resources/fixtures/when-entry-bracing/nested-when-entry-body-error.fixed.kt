package sample

fun classify(x: Int, y: Boolean): String {
    return when (x) {
        1 -> {
            "one"
        }
        2 -> when (y) {
            true -> {
                "yes"
            }
            false -> {
                "no"
            }
        }
        else -> {
            "other"
        }
    }
}