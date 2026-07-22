package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        bar()
    }
}

fun bar() {}

// expect-error 8:14 swallowed-exception "The caught exception is swallowed. The original exception could be lost."
