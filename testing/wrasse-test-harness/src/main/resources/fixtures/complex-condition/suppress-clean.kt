package sample

@Suppress("complex-condition")
fun check(a: Boolean, b: Boolean, c: Boolean, d: Boolean): Boolean {
    return if (a && b && c && d) true else false
}

// expect-clean
