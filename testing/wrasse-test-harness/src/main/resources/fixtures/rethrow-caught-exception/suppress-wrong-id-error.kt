package sample

import java.io.IOException

@Suppress("no-semicolons")
fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        throw e
    }
}

// expect-error 10:9 rethrow-caught-exception "Do not rethrow a caught exception of the same type."
