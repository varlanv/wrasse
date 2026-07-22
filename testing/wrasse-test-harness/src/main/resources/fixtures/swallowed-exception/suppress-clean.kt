package sample

import java.io.IOException

@Suppress("swallowed-exception")
fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        bar()
    }
}

fun bar() {}

// expect-clean
