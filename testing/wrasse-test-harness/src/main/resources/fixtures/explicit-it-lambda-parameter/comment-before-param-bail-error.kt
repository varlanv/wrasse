package sample

fun use() {
    listOf(1).map { /* c */ it -> it.plus(1) }
}

// expect-error 4:29 explicit-it-lambda-parameter "Explicit 'it' lambda parameter is redundant (no autofix for this shape)"
