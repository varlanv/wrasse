package sample

fun foo() {
    try {
        println("work")
    } catch (expected: Exception) {
    }
}

// expect-clean
