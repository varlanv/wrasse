package sample

fun <T> describe(value: T): String where T : CharSequence {
    return value.toString()
}

// expect-error 3:59 function-expression-body "Function body should be replaced with body expression"
