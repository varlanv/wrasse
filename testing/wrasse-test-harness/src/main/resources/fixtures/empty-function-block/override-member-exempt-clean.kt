package sample

open class Base {
    open fun stuff() {
        println("base")
    }
}

class Derived : Base() {
    override fun stuff() {
    }
}

// expect-clean
