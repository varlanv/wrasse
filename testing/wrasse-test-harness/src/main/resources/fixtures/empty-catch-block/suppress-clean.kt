package sample

@Suppress("empty-catch-block")
fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
    }
}

// expect-clean
