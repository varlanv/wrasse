package sample

@Suppress("no-semicolons")
fun f(a: Int, b: Int, c: Int, d: Int, e: Int, g: Int) {
}

// expect-error 4:6 long-parameter-list "The function has 6 parameters; the maximum allowed is 5"
