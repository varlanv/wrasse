package sample

fun f(a: Int, b: Int, c: Int): Int {
    if (a > 0) return 1
    if (b > 0) return 2
    if (c > 0) return 3
    return 0
}

fun g(a: Int, b: Int): Int {
    if (a > 0) return 1
    if (b > 0) return 2
    return 0
}

// expect-error 3:5 cyclomatic-complexity "Function 'f' has a cyclomatic complexity of 4; the maximum allowed is 3"
