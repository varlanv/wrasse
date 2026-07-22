package sample

open class Base
class Derived : Base()

fun Base.process(x: Int): String = "base"
fun process(x: Int): String = "top-level"

// expect-clean
