package sample

fun functionReturningConstantString() = "1"

// expect-error 3:5 function-only-returning-constant "Function 'functionReturningConstantString' only returns a constant; consider declaring a constant instead"
