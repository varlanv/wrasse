package sample

class Foo(val x: Int) {
    @Suppress("empty-secondary-constructor")
    constructor() : this(0) {
    }
}

// expect-clean
