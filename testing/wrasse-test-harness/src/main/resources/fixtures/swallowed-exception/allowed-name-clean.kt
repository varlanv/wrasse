package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (ignored: IOException) {
        bar()
    }
}

fun bar() {}

// expect-clean
