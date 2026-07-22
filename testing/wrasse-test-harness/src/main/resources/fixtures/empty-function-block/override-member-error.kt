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

// expect-error 10:26 empty-function-block "Empty function block detected. Empty blocks of code serve no purpose and should be removed"
