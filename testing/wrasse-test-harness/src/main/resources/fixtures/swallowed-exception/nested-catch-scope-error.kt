package sample

import java.io.IOException

fun foo() {
    try {
        bar()
    } catch (e: IOException) {
        try {
            bar()
        } catch (e: Exception) {
            println(e)
        }
    }
}

fun bar() {}

// expect-error 8:14 swallowed-exception "The caught exception is swallowed. The original exception could be lost."
