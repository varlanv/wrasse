package sample

class Foo(val x: Int) {
    constructor() : this(0) {
    }
}

// expect-error 4:29 empty-secondary-constructor "Empty secondary constructor detected. Empty blocks of code serve no purpose and should be removed"
