package sample

fun main(vararg args: String) {
    throw IllegalStateException("boom")
}

// expect-error 3:1 throwing-exception-in-main "The main function should not throw an exception"
