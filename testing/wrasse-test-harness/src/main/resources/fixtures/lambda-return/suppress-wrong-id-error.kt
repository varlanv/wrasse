package sample

fun start(block: (Int) -> Int): Int = block(1)

@Suppress("no-semicolons")
fun run(): Int {
    return start {
        return@start it
    }
}

// expect-error 8:9 lambda-return "Unnecessary labeled return as the last statement in a lambda"
