package sample

interface Greeter {
    fun greet(a: Int): String
}

abstract class Base {
    abstract fun add(a: Int): Int
}

// expect-clean
