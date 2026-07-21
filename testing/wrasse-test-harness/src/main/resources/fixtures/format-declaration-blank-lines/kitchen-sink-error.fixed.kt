package sample

class Foo

class Bar

class Holder {
    val a = 1

    fun show(): Int = a

    // trailing comment before baz
    fun baz(): Int = a + 1
}

fun outer() {
    val x = 1

    fun helper(): Int = x
    println(helper())
}