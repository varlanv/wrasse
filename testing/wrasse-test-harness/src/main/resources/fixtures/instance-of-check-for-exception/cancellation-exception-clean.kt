package sample

class CancellationException : Exception()

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        if (e is CancellationException) {
            throw e
        }
    }
}

// expect-clean
