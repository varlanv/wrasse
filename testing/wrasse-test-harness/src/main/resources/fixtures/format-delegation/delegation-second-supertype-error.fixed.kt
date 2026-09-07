package sample

interface Greeter {
    fun greet(): String
}

open class Base

class Wrapper(private val impl: Greeter) : Base(), Greeter by impl