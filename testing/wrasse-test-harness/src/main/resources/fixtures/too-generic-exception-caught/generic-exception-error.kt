package sample

fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        println(e)
    }
}

// expect-error 6:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
