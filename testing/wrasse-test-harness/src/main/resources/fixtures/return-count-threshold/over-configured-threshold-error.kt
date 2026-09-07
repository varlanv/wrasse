package sample

fun f(a: Int): Int {
    if (a > 0) return 1
    return 0
}

// expect-error 3:5 return-count "Function 'f' has 2 return statements; the maximum allowed is 1"
