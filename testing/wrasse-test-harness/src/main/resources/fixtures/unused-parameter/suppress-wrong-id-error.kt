package sample

@Suppress("no-semicolons")
fun foo(unused: String) {
    println("hi")
}

// expect-error 4:9 unused-parameter "Function parameter 'unused' is unused"
