package sample

fun start(block: (Int) -> Int): Int = block(1)

@Suppress("lambda-return")
fun run(): Int {
    return start {
        return@start it
    }
}

// expect-clean
