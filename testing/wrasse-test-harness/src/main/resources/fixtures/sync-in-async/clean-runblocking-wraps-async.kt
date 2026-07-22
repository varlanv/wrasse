package sample

fun async(block: () -> Unit) {}
fun runBlocking(block: () -> Unit) {}

fun start() {
    runBlocking {
        async {
            println("fine, async is nested inside runBlocking, not the other way around")
        }
    }
}

// expect-clean
