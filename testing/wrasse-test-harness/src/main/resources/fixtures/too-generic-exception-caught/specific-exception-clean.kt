package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        println(e)
    }
}

// expect-clean
