package sample

fun async(block: () -> Unit) {}
fun runBlocking(block: () -> Unit) {}

@Suppress("sync-in-async")
fun start() {
    async {
        runBlocking {
            println("blocking")
        }
    }
}

// expect-clean
