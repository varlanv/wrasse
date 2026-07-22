package sample

fun functionReturningConstantChar() = '1'

// expect-error 3:5 function-only-returning-constant "Function 'functionReturningConstantChar' only returns a constant; consider declaring a constant instead"
