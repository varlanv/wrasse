package sample

fun build(items: List<String>): String {
    val joined = items.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(separator = ", ")
    return joined
}