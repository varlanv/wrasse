package sample

open class Base {
    override fun toString(): String = "Base"
}

class Derived : Base() {
    override fun toString(): String {
        return "${super.toString()}!"
    }
}

// expect-clean
