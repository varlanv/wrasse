package sample

open class Base
class Derived : Base()

fun Base.process(x: Int): String = "base"
fun Derived.process(x: Int): String = "derived"

// expect-error 6:1 extension-functions-same-name "Extension function 'process' on 'Base' has the same signature as one on related class 'Derived'"
// expect-error 7:1 extension-functions-same-name "Extension function 'process' on 'Derived' has the same signature as one on related class 'Base'"
