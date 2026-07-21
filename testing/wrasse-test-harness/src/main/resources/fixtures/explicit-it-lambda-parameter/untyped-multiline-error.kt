package sample

fun use() {
    listOf(1).forEach { it ->
        it.plus(1)
    }
}

// expect-error 4:25 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant"
