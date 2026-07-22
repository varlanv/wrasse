package sample

fun foo() {
    try {
        println("work")
    } catch (ignore: Exception) {
        try {
            println("more work")
        } catch (e: Exception) {
        }
    }
}

// expect-error 9:32 empty-catch-block "Empty catch block detected. Empty catch blocks indicate that an exception is ignored and not handled."
