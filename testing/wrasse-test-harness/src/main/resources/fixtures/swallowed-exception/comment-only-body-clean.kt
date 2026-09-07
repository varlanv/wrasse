package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        // intentionally ignored
    }
}

// expect-clean
