package sample

@Suppress("no-semicolons")
fun f(a1: Boolean, a2: Boolean, a3: Boolean, a4: Boolean, a5: Boolean, a6: Boolean, a7: Boolean, a8: Boolean, a9: Boolean, a10: Boolean, a11: Boolean, a12: Boolean, a13: Boolean, a14: Boolean) {
    if (a1) {}
    if (a2) {}
    if (a3) {}
    if (a4) {}
    if (a5) {}
    if (a6) {}
    if (a7) {}
    if (a8) {}
    if (a9) {}
    if (a10) {}
    if (a11) {}
    if (a12) {}
    if (a13) {}
    if (a14) {}
}

// expect-error 4:5 cyclomatic-complexity "Function 'f' has a cyclomatic complexity of 15; the maximum allowed is 14"
