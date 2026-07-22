package sample

fun runBlocking(block: () -> Unit) {}

fun start() {
    runBlocking {
        println("fine at the top level")
    }
}

// expect-clean
