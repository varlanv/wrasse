package sample

fun foo(): Nothing {
    throw IllegalStateException("bad")
}

// expect-error 3:20 function-expression-body "Function body should be replaced with body expression"
