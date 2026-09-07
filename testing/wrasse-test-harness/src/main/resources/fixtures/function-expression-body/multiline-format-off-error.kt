package sample

fun foo(): String {
    return listOf("a", "b")
        .joinToString()
}

// expect-error 3:19 function-expression-body "Function body should be replaced with body expression (no autofix for this shape)"
