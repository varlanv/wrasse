package sample

@Suppress("no-such-rule")
fun Foo() {
}

// expect-error 4:5 function-naming "Function name should start with a lowercase letter (except factory methods) and use camel case"
