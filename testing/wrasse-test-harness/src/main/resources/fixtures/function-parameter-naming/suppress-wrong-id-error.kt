package sample

@Suppress("no-semicolons")
fun foo(BadName: Int) {
}

// expect-error 4:9 function-parameter-naming "Function parameter name should start with a lowercase letter and use camel case"
