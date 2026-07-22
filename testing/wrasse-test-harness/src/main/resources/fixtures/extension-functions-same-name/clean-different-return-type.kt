package sample

open class Base
class Derived : Base()

fun Base.process(x: Int): String = "base"
fun Derived.process(x: Int): Int = x

// expect-clean
