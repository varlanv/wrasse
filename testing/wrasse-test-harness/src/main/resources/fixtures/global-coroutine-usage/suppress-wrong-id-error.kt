package sample

object GlobalScope {
    fun launch(block: () -> Unit) {}
}

@Suppress("no-semicolons")
fun start() {
    GlobalScope.launch { }
}

// expect-error 9:5 global-coroutine-usage "This use of GlobalScope should be replaced by a CoroutineScope or coroutineScope"
