package sample

fun check(a: Boolean, b: Boolean): Boolean {
    return a && b && a
}

// expect-error 4:12 unnecessary-part-of-binary-expression "This binary expression repeats one of its own operands unnecessarily"
