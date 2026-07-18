package sample

class Foo {
    ;
    val x = 1
}

// expect-error 4:5 no-semicolons "Unnecessary semicolon"
