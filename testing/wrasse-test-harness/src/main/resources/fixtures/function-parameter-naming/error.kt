package sample

fun foo(BadName: Int) {
}

// expect-error 3:9 function-parameter-naming "Function parameter name should start with a lowercase letter and use camel case"
