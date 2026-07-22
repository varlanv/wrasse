package sample

open class Base
class Derived : Base()

fun Base.process(x: Int): String = "base"
fun Derived.process(y: Int): String = "derived"

// expect-clean
