package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
    }
}

// expect-error 6:28 empty-catch-block "Empty catch block detected. Empty catch blocks indicate that an exception is ignored and not handled."
