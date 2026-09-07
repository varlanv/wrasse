package sample

fun foo(a: Boolean, b: Boolean): Boolean {
    return !(!(a && b))
}

// expect-error 4:12 double-negative "Expression negated more than once; this can be simplified"
