package sample

fun foo(isValid: Boolean): Boolean {
    return !!isValid
}

// expect-error 4:12 double-negative "Expression negated more than once; this can be simplified"
