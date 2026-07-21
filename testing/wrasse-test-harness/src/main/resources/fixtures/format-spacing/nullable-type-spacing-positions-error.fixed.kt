package sample

val label: String? = null

fun String?.shout(): String = this ?: ""

fun wrap(values: List<String?>): List<String>? {
    return values.filterNotNull()
}