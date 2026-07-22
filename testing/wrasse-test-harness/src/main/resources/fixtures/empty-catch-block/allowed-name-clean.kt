package sample

fun foo() {
    try {
        println("work")
    } catch (ignored: Exception) {
    }
}

// expect-clean
