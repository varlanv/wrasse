package sample

fun foo(a: Boolean, b: Boolean): Boolean {
    if (!(!a) && b) {
        return true
    }
    return false
}

// expect-error 4:9 double-negative "Expression negated more than once; this can be simplified"
