package sample

class MyException : Exception()

@Suppress("instance-of-check-for-exception")
fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        if (e is MyException) {
            println("specific")
        }
    }
}

// expect-clean
