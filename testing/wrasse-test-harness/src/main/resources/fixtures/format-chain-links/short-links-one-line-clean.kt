package sample

fun join(items: List<String>): String = items.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

fun single(items: List<String>): List<String> = items.map {
    val trimmed = it.trim()
    trimmed.uppercase()
}

// expect-clean
