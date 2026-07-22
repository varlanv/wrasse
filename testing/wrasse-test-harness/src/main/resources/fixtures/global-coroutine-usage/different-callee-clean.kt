package sample

object GlobalScope {
    fun cancel() {}
}

fun stop() {
    GlobalScope.cancel()
}

// expect-clean
