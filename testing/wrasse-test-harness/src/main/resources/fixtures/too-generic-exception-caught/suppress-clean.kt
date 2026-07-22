package sample

@Suppress("too-generic-exception-caught")
fun foo() {
    try {
        println("work")
    } catch (e: Exception) {
        println(e)
    }
}

// expect-clean
