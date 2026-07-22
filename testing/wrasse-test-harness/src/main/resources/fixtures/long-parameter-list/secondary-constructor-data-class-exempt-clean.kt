package sample

data class Foo(val a: Int) {
    constructor(a: Int, b: Int, c: Int, d: Int, e: Int, g: Int, h: Int) : this(a)
}

// expect-clean
