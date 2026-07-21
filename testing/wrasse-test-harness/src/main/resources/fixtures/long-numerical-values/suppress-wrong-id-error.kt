package sample

class Foo {
    @Suppress("no-such-rule")
    val x = 1000000
}

// expect-error 5:13 long-numerical-values "Long numerical literal without underscore separators"
