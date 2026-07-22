package sample

fun outer(block: (Int) -> Int): Int = block(1)
fun inner(block: (Int) -> Int): Int = block(2)

fun run(): Int {
    return outer { x ->
        return@outer inner { y ->
            return@inner y
        }
    }
}

// expect-error 8:9 lambda-return "Unnecessary labeled return as the last statement in a lambda"
// expect-error 9:13 lambda-return "Unnecessary labeled return as the last statement in a lambda"
