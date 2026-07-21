package sample

fun outer() {
    val x = 1

    fun helper(): Int = x + 1
    println(helper())
}