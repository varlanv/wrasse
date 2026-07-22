package sample

import java.io.IOException

fun foo() {
    try {
        println("work")
    } catch (e: IOException) {
        throw e
    } catch (e: Exception) {
        println(e.message)
    }
}

// expect-clean
