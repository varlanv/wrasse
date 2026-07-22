package sample

fun main() {
    fun helper() {
        throw IllegalStateException("boom")
    }
    helper()
}

// expect-error 3:1 throwing-exception-in-main "The main function should not throw an exception"
