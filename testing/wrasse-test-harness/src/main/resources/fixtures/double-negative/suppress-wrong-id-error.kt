package sample

@Suppress("no-such-rule")
fun foo(isValid: Boolean): Boolean {
    return !!isValid
}

// expect-error 5:12 double-negative "Expression negated more than once; this can be simplified"
