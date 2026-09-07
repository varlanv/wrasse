package sample

interface Greeter {
    fun greet(): String
}

interface Marker

class W3(private val impl: Greeter) : Greeter by impl, Marker