package sample

class Foo {
    @Suppress("no-semicolons")
    constructor(x: Int) {
        println(x);
    }
}

// expect-clean
