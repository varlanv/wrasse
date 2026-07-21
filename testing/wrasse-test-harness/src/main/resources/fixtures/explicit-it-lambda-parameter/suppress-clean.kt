package sample

@Suppress("explicit-it-lambda-parameter")
fun use() {
    listOf(1).map { it -> it.plus(1) }
}

// expect-clean
