package sample

open class Base
class DerivedA : Base()
class DerivedB : Base()

fun Base.process(x: Int): String = "base"
fun DerivedA.process(x: Int): String = "derivedA"
fun DerivedB.process(x: Int): String = "derivedB"

// expect-error 7:1 extension-functions-same-name "Extension function 'process' on 'Base' has the same signature as one on related class 'DerivedA'"
// expect-error 8:1 extension-functions-same-name "Extension function 'process' on 'DerivedA' has the same signature as one on related class 'Base'"
// expect-error 7:1 extension-functions-same-name "Extension function 'process' on 'Base' has the same signature as one on related class 'DerivedB'"
// expect-error 9:1 extension-functions-same-name "Extension function 'process' on 'DerivedB' has the same signature as one on related class 'Base'"
