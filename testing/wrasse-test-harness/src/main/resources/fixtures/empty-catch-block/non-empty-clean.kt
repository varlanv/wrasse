package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        println(e)
    }
}

// expect-clean
