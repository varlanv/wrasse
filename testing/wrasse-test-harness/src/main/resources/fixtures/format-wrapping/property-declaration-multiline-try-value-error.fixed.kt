package sample

fun example(): Int {
    val x =
        try {
            1
        } catch (e: Exception) {
            2
        }
    return x
}