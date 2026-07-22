package sample

@Suppress("mixed-condition-operators")
fun check(a: Boolean, b: Boolean, c: Boolean): Boolean {
    return a && b || c
}

// expect-clean
