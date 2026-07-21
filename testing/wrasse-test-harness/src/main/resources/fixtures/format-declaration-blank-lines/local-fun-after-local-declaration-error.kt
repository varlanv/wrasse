package sample

fun outer() {
    val x = 1
    fun helper(): Int = x + 1
    println(helper())
}

// expect-error 1:1 format "File is not wrasse-formatted"
