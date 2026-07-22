package sample

@Suppress("double-negative")
fun foo(isValid: Boolean): Boolean {
    return !!isValid
}

// expect-clean
