package sample

object GlobalScope {
    fun launch(block: () -> Unit) {}
}

@Suppress("global-coroutine-usage")
fun start() {
    GlobalScope.launch { }
}

// expect-clean
