package sample

import java.io.IOException

@Suppress("rethrow-caught-exception")
fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        throw e
    }
}

// expect-clean
