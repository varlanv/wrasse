package sample

fun start(block: (Int) -> Int): Int = block(1)

fun run(): Int {
    return start {
        if (it > 0) {
            return@start it
        }
        it + 1
    }
}

// expect-clean
