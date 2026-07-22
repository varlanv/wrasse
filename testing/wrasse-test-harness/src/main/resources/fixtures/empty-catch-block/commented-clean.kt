package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        // intentionally ignored
    }
}

// expect-clean
