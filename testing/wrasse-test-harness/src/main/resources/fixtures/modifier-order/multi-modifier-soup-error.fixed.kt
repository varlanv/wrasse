package sample

abstract class Foo4 {
    protected open val v = ""
    internal open suspend fun f(v: Any): Any = ""
    protected lateinit var lv: String
}