package sample

fun String.shout(): String {
    return uppercase()
}

// expect-error 3:28 function-expression-body "Function body should be replaced with body expression"
