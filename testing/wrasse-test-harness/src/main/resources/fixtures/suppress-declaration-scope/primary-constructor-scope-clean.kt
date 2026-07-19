package sample

class Foo @Suppress("no-semicolons") constructor(cb: () -> Unit = { println("x"); }) {
    init {
        cb()
    }
}

// expect-clean
