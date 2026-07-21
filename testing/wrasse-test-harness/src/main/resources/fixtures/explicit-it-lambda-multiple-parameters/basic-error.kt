package sample

fun demo(list: List<Int>) {
    list.zipWithNext { it, next -> it + next }
}

// expect-error 4:22 explicit-it-lambda-multiple-parameters "'it' should not be used as a name for a lambda parameter when the lambda declares more than one parameter"
