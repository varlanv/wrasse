package sample

fun functionReturningConstantEscapedString(str: String) = "str: \$str"

// expect-error 3:5 function-only-returning-constant "Function 'functionReturningConstantEscapedString' only returns a constant; consider declaring a constant instead"
