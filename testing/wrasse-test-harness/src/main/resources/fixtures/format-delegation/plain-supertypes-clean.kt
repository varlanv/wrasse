package sample

interface Greeter {
    fun greet(): String
}

open class Base

class Plain : Base(), Greeter {
    override fun greet(): String = "hi"
}

// expect-clean
