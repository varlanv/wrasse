package sample

fun foo(unused: String) {
    println("hi")
}

// expect-error 3:9 unused-parameter "Function parameter 'unused' is unused"
