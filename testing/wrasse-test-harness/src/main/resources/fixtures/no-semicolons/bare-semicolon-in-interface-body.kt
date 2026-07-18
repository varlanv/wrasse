package sample

interface Foo {
    ;
    val x: Int
}

// expect-error 4:5 no-semicolons "Unnecessary semicolon"
