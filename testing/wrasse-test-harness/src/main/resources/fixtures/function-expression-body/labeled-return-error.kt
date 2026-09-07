package sample

fun foo(): String {
    return@foo "value"
}

// expect-error 3:19 function-expression-body "Function body should be replaced with body expression (no autofix for this shape)"
