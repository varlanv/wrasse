package sample

fun classify(items: List<Int>): String = when (val first = items.firstOrNull()) {
    null -> "empty"
    else -> "many"
}

// expect-clean
