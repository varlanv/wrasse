package sample

@Suppress("no-" + "semicolons")
class Foo {
    val x = 1;
}

// expect-error 5:14 no-semicolons "Unnecessary semicolon"
