package sample

fun classify(items: List<Int>) {
    when (
        val first = items.firstOrNull()
    ) {
        null -> println("empty")
        else -> println("many")
    }
}