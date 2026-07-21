package sample

fun f(a: Int, b: Int, c: Int, d: Int, e: Int, g: Int) {
}

// expect-error 3:6 long-parameter-list "The function has 6 parameters; the maximum allowed is 5"
