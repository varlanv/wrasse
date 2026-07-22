package sample

@Suppress("unnecessary-part-of-binary-expression")
fun check(a: Boolean, b: Boolean): Boolean {
    return a && b && a
}

// expect-clean
