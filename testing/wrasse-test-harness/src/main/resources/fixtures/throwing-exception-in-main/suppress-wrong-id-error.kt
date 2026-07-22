package sample

@Suppress("no-semicolons")
fun main() {
    throw IllegalStateException("boom")
}

// expect-error 3:1 throwing-exception-in-main "The main function should not throw an exception"
