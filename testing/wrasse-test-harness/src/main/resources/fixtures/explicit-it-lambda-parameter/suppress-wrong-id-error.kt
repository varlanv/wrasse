package sample

@Suppress("no-such-rule")
fun use() {
    listOf(1).map { it -> it.plus(1) }
}

// expect-error 5:21 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant"
