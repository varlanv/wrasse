package sample

class Foo {
    @Suppress("trivial-accessors")
    val prop: Int = 0
        get() = field
}

// expect-clean
