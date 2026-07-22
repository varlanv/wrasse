package sample

@Suppress("no-semicolons")
fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        println(e)
    }
}

// expect-error 7:14 too-generic-exception-caught "The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled."
