package sample

open class Base {
    open fun value(): Int = 1
}

class Derived : Base() {
    override fun value(): Int = 1
}

// expect-clean
