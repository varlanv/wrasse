package sample

fun foo(): Unit {
}

fun bar() {
    return foo()
}

// expect-error 6:11 function-expression-body "Function body should be replaced with body expression (no autofix for this shape)"
