package sample

class Foo {
    val prop: Int = 0
        get() { return 42 }
}

// expect-clean
