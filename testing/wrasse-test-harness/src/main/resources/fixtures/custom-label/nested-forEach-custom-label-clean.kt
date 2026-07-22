package sample

val matrix: List<List<Int>> = listOf(listOf(1, 2), listOf(3, 4))

fun foo() {
    matrix.forEach outer@{
        it.forEach {
            if (it == 21) {
                return@outer
            }
        }
    }
}

// expect-clean
