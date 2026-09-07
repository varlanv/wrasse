package sample

interface Greeter {
    fun greet(): String
}

class Sole(private val impl: Greeter) : Greeter by impl

// expect-clean
