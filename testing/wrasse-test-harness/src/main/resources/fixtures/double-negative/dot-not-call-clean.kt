package sample

fun foo(isValid: Boolean): Boolean {
    return isValid.not().not()
}

// expect-clean
