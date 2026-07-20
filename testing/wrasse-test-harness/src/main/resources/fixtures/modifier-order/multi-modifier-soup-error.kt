package sample

abstract class Foo4 {
    open protected val v = ""
    open suspend internal fun f(v: Any): Any = ""
    lateinit protected var lv: String
}

// expect-error 4:5 modifier-order "Modifiers out of order, expected: protected open"
// expect-error 5:5 modifier-order "Modifiers out of order, expected: internal open suspend"
// expect-error 6:5 modifier-order "Modifiers out of order, expected: protected lateinit"
