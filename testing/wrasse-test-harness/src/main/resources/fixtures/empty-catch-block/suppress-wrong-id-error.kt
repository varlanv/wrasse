package sample

@Suppress("no-semicolons")
fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
    }
}

// expect-error 7:28 empty-catch-block "Empty catch block detected. Empty catch blocks indicate that an exception is ignored and not handled."
