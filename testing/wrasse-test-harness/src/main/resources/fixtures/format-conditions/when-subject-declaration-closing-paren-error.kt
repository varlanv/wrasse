package sample

fun classify(items: List<Int>): String {
    val label = when (
        val first = items.firstOrNull()
        ) {
        null -> "empty"
        1 -> "one"
        else -> "many"
    }
    return label
}

// expect-error 1:1 format "File is not wrasse-formatted"
