package sample

fun foo() {
    throw IllegalStateException("bad")
}

// expect-error 3:11 function-expression-body "Function body should be replaced with body expression"
