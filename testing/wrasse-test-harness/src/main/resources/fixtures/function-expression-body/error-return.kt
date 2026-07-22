package sample

fun foo(): String {
    return "foo"
}

// expect-error 3:19 function-expression-body "Function body should be replaced with body expression"
