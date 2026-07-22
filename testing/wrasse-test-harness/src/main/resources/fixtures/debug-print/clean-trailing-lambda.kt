package sample

fun print(block: () -> Unit) {
    block()
}

fun run() {
    print {}
}

// expect-clean
