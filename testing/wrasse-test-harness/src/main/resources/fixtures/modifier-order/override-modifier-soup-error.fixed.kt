package sample

open class Base16 {
    open val v: String = ""
    open suspend fun f(v: Any): Any = ""
    open fun foo(bar: String): String = foo(bar.substringBeforeLast(" "))
}

class Foo16 : Base16() {
    public override val v = ""
    override suspend fun f(v: Any): Any = ""
    override tailrec fun foo(bar: String): String = foo(bar.substringBeforeLast(" "))
}