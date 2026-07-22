package sample

class Foo {
    init {
        throw IllegalStateException()
    }
}

// expect-clean
