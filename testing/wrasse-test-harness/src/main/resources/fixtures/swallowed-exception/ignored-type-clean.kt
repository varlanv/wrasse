package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: NumberFormatException) {
        bar()
    }
}

fun bar() {}

// expect-clean
