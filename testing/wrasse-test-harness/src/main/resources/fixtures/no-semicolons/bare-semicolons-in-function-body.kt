package sample

fun foo() {
    ;
    bar()
    ;

    bar()

    ;
}

fun bar() {}

// expect-error 4:5 no-semicolons "Unnecessary semicolon"
// expect-error 6:5 no-semicolons "Unnecessary semicolon"
// expect-error 10:5 no-semicolons "Unnecessary semicolon"
