package sample

fun classify(x: Int): String = when (x) { 1 -> {
        "one"
    }
    2 -> {
        "two"
    }
    else -> {
        "other"
    }
}