package sample

open class Base {
    protected open val _bar: Int = 0
}

class Foo : Base() {
    override val _bar: Int = 1
}

// expect-clean
