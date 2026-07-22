package sample

interface Marker

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        if (e is Marker) {
            println("marker")
        }
    }
}

// expect-error 9:13 instance-of-check-for-exception "Instead of catching for a general exception type and checking for a specific exception type, use multiple catch blocks."
