package sample

@Suppress("no-semicolons")
fun functionReturningConstantString() = "1"

// expect-error 4:5 function-only-returning-constant "Function 'functionReturningConstantString' only returns a constant; consider declaring a constant instead"
