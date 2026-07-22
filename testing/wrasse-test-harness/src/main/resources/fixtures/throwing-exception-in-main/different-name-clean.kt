package sample

fun run() {
    throw IllegalStateException("boom")
}

// expect-clean
