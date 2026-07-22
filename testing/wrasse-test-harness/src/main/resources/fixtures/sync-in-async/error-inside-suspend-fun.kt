package sample

fun runBlocking(block: () -> Unit) {}

suspend fun start() {
    runBlocking {
        println("blocking")
    }
}

// expect-error 6:5 sync-in-async "runBlocking() called from inside asynchronous code (async/launch/suspend); this blocks the thread"
