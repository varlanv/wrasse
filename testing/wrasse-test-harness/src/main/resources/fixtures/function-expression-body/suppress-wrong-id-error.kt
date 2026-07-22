package sample

@Suppress("no-such-rule")
fun foo(): String {
    return "foo"
}

// expect-error 4:19 function-expression-body "Function body should be replaced with body expression"
