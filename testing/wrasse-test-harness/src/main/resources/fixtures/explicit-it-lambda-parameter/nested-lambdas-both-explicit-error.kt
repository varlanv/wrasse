package sample

fun use() {
    listOf(listOf(1)).map { it ->
        it.map { it -> it.plus(1) }
    }
}

// expect-error 4:29 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant"
// expect-error 5:18 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant"
