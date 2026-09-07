package sample

open class Base {
    override fun hashCode(): Int = 1
}

class Derived : Base() {
    override fun toString(): String {
        return "${super.hashCode()}!"
    }
}