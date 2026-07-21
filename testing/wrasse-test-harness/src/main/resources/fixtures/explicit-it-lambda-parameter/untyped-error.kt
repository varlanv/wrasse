package sample

fun use() {
    listOf(1).map { it -> it.plus(1) }
}

// expect-error 4:21 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant"
