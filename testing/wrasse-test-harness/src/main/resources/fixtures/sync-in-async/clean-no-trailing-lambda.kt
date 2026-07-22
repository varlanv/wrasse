package sample

fun async(block: () -> Unit) {}
fun runBlocking(block: () -> Unit) {}

fun start() {
    async {
        runBlocking(block = { println("blocking") })
    }
}

// expect-clean
