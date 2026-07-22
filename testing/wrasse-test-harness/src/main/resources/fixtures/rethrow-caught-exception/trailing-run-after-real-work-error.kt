package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        println(e.message)
        throw e
    } catch (e: Exception) {
        throw e
    }
}

// expect-error 12:9 rethrow-caught-exception "Do not rethrow a caught exception of the same type."
