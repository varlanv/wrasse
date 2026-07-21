package sample

class Foo {
    @Suppress("no-such-rule")
    val prop: Int = 0
        get() = field
}

// expect-error 6:9 trivial-accessors "Trivial accessor"
