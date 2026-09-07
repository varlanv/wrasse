package sample

fun report(items: List<String>): List<String> {
    val out = items.filter { it.isNotEmpty() }.map {
        val trimmed = it.trim()
        trimmed.uppercase()
    }
    return out
}

// expect-error 1:1 format "File is not wrasse-formatted"
