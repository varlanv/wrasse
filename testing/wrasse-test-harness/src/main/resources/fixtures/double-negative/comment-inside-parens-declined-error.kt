package sample

fun foo(isValid: Boolean): Boolean {
    return !(/* keep */ !isValid)
}

// expect-error 4:12 double-negative "Expression negated more than once; this can be simplified (no autofix for this shape)"
