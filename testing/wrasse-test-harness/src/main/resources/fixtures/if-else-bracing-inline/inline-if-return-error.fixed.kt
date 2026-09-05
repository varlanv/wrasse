package sample

fun guard(condition: Boolean) {
    if (condition) {
        return
    }
    println("continue")
}