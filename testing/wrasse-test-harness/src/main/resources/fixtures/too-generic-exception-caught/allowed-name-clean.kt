package sample

fun foo() {
    try {
        println("work")
    } catch (ignored: Exception) {
        println("ignored")
    }
}

// expect-clean
