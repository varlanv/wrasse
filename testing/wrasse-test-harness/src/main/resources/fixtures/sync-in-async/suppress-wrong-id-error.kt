package sample

fun async(block: () -> Unit) {}
fun runBlocking(block: () -> Unit) {}

@Suppress("no-such-rule")
fun start() {
    async {
        runBlocking {
            println("blocking")
        }
    }
}

// expect-error 9:9 sync-in-async "runBlocking() called from inside asynchronous code (async/launch/suspend); this blocks the thread"
