package sample

fun demo(list: List<Int>) {
    list.mapIndexed { index, it -> it + index }
}

// expect-error 4:21 explicit-it-lambda-multiple-parameters "'it' should not be used as a name for a lambda parameter when the lambda declares more than one parameter"
