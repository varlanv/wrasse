package sample

open class Base
class Derived : Base()

@Suppress("no-such-rule")
fun Base.process(x: Int): String = "base"

@Suppress("no-such-rule")
fun Derived.process(x: Int): String = "derived"

// expect-error 6:1 extension-functions-same-name "Extension function 'process' on 'Base' has the same signature as one on related class 'Derived'"
// expect-error 9:1 extension-functions-same-name "Extension function 'process' on 'Derived' has the same signature as one on related class 'Base'"
