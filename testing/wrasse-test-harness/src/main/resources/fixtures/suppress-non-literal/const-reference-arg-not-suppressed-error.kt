package sample

const val SOME_ID = "no-semicolons"

@Suppress(SOME_ID)
class Foo {
    val x = 1;
}

// expect-error 7:14 no-semicolons "Unnecessary semicolon"
