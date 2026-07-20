package sample

abstract class Base17 {
    abstract fun test()
}

abstract class Foo17 : Base17() {
    override open fun test() {}
}

// expect-error 8:5 modifier-order "Modifiers out of order, expected: open override"
