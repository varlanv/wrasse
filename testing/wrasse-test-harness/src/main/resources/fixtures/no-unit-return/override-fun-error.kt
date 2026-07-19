package sample

interface Greeter {
    fun greet(): Unit
}

class SimpleGreeter : Greeter {
    override fun greet(): Unit {}
}

// expect-error 8:25 no-unit-return "Redundant Unit return type"
