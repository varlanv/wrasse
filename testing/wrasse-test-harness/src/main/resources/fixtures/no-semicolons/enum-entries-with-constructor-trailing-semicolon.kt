package sample

enum class Foo1(bar: String) {
    A(1),
    B(2);
}

enum class Foo3(bar: String) {
    ;
}

// expect-error 5:9 no-semicolons "Unnecessary semicolon"
// expect-error 9:5 no-semicolons "Unnecessary semicolon"
