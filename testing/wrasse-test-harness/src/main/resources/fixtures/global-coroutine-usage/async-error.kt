package sample

object GlobalScope {
    fun launch(block: () -> Unit) {}
    fun async(block: () -> Unit) {}
}

fun start() {
    GlobalScope.async { }
}

// expect-error 9:5 global-coroutine-usage "This use of GlobalScope should be replaced by a CoroutineScope or coroutineScope"
