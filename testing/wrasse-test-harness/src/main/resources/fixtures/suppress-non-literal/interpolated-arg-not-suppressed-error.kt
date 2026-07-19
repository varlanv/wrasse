package sample

const val SUFFIX = "semicolons"

@Suppress("no-$SUFFIX")
class Foo {
    val x = 1;
}

// expect-error 7:14 no-semicolons "Unnecessary semicolon"
