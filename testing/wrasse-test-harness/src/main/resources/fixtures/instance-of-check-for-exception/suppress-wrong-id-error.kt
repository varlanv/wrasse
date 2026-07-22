package sample

class MyException : Exception()

@Suppress("no-semicolons")
fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        if (e is MyException) {
            println("specific")
        }
    }
}

// expect-error 10:13 instance-of-check-for-exception "Instead of catching for a general exception type and checking for a specific exception type, use multiple catch blocks."
