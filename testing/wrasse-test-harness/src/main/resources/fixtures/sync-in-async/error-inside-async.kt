package sample

fun async(block: () -> Unit) {}
fun runBlocking(block: () -> Unit) {}

fun start() {
    async {
        runBlocking {
            println("blocking")
        }
    }
}

// expect-error 8:9 sync-in-async "runBlocking() called from inside asynchronous code (async/launch/suspend); this blocks the thread"
