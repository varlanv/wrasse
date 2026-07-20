package sample

open class Base16 {
    open val v: String = ""
    open suspend fun f(v: Any): Any = ""
    open fun foo(bar: String): String = foo(bar.substringBeforeLast(" "))
}

class Foo16 : Base16() {
    override public val v = ""
    suspend override fun f(v: Any): Any = ""
    tailrec override fun foo(bar: String): String = foo(bar.substringBeforeLast(" "))
}

// expect-error 10:5 modifier-order "Modifiers out of order, expected: public override"
// expect-error 11:5 modifier-order "Modifiers out of order, expected: override suspend"
// expect-error 12:5 modifier-order "Modifiers out of order, expected: override tailrec"
