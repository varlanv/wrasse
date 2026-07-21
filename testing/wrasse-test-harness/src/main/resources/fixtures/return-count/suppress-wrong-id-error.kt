package sample

@Suppress("no-semicolons")
fun f(x: Int): Int {
    if (x > 0) {
        return 1
    }
    if (x < 0) {
        return 2
    }
    return 3
}

// expect-error 4:5 return-count "Function 'f' has 3 return statements; the maximum allowed is 2"
