package sample

class Foo() {
    constructor(a: Int, b: Int, c: Int, d: Int, e: Int, g: Int, h: Int) : this()
}

// expect-error 4:16 long-parameter-list "The constructor has 7 parameters; the maximum allowed is 6"
