package sample

fun demo() {
    val lambda = { it: Int, that: String -> it.toString() + that }
}

// expect-error 4:18 explicit-it-lambda-multiple-parameters "'it' should not be used as a name for a lambda parameter when the lambda declares more than one parameter"
