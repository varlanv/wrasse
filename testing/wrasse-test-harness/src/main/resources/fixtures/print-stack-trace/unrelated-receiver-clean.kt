package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        val other = RuntimeException()
        other.printStackTrace()
    }
}

// expect-clean
