package sample

fun demo(value: Int?): String {
    return value?.toString() ?: "none"
}