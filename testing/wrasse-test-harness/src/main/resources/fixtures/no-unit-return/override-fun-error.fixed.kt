package sample

interface Greeter {
    fun greet(): Unit
}

class SimpleGreeter : Greeter {
    override fun greet() {}
}