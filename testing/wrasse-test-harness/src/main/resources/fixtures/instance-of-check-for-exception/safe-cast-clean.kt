package sample

class MyException : Exception()

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        val specific = e as? MyException
        println(specific)
    }
}

// expect-clean
