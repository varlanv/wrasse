package sample

open class Base
class Derived : Base()

@Suppress("extension-functions-same-name")
fun Base.process(x: Int): String = "base"

@Suppress("extension-functions-same-name")
fun Derived.process(x: Int): String = "derived"

// expect-clean
