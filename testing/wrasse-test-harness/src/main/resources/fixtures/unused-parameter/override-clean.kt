package sample

open class Base {
    open fun foo(unused: String) {}
}

class Derived : Base() {
    override fun foo(unused: String) {}
}

// expect-clean
