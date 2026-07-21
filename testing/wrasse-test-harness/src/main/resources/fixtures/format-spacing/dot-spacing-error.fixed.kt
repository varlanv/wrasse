package sample

fun demo(text: String, numbers: List<Int>): Int {
    val length = text.length
    return length + numbers.first()
}