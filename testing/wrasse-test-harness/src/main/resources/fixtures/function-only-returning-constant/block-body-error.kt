package sample

fun getValue(): Int {
    return 42
}

// expect-error 3:5 function-only-returning-constant "Function 'getValue' only returns a constant; consider declaring a constant instead"
