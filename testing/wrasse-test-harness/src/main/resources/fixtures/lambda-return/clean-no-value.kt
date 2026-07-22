package sample

fun start(block: () -> Unit) {
    block()
}

fun run() {
    start {
        return@start
    }
}

// expect-clean
