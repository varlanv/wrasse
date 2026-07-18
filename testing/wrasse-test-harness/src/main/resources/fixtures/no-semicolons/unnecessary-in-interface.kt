package sample

interface Foo {
    val x: Int;
}

// expect-error 4:15 no-semicolons "Unnecessary semicolon"
