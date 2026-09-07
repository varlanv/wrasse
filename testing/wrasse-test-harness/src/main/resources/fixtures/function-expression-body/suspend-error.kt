package sample

suspend fun foo(): String {
    return "foo"
}

// expect-error 3:27 function-expression-body "Function body should be replaced with body expression"
