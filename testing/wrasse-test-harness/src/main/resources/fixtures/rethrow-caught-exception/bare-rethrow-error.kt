package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        throw e
    }
}

// expect-error 9:9 rethrow-caught-exception "Do not rethrow a caught exception of the same type."
