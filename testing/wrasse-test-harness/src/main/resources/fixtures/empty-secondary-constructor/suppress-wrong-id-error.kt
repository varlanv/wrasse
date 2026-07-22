package sample

class Foo(val x: Int) {
    @Suppress("no-such-rule")
    constructor() : this(0) {
    }
}

// expect-error 5:29 empty-secondary-constructor "Empty secondary constructor detected. Empty blocks of code serve no purpose and should be removed"
