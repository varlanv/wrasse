package sample

enum class E1 {
    A,
    B;
}

enum class E2 {
    A,
    B // comment
    ;
}

enum class E3 {
    ;
}

enum class E4 {
    // comment
    ;
}

// expect-error 5:6 no-semicolons "Unnecessary semicolon"
// expect-error 11:5 no-semicolons "Unnecessary semicolon"
// expect-error 15:5 no-semicolons "Unnecessary semicolon"
// expect-error 20:5 no-semicolons "Unnecessary semicolon"
