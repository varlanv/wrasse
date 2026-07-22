package sample

open class Base {
    override fun hashCode(): Int = 1
}

class Derived : Base() {
    override fun toString(): String {
        return "${super.hashCode().toString()}!"
    }
}

// expect-error 9:19 redundant-to-string-in-template "Redundant '.toString()' call in string template"
