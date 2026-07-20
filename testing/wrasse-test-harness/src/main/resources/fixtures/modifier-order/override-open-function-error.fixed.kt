package sample

abstract class Base17 {
    abstract fun test()
}

abstract class Foo17 : Base17() {
    open override fun test() {}
}