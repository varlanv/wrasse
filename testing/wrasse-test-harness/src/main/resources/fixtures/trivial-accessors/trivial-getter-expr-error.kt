package sample

class Foo {
    val prop: Int = 0
        get() = field
}

// expect-error 5:9 trivial-accessors "Trivial accessor"
