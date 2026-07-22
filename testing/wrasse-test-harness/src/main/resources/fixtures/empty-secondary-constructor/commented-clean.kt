package sample

class Foo(val x: Int) {
    constructor() : this(0) {
        // no-op
    }
}

// expect-clean
