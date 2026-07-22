package sample

class Foo(val x: Int) {
    constructor() : this(0) {
        println("constructed")
    }
}

// expect-clean
