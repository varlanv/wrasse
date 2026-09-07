package sample

interface Greeter {
    fun greet(): String
}

open class Base

class Wrapper : Base(), Greeter by object : Greeter {
        override fun greet(): String = "hi"
    }

// expect-error 1:1 format "File is not wrasse-formatted"
