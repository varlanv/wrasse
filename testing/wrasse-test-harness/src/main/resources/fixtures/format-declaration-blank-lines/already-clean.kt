package sample

class Foo

class Bar

class Holder {
    val a = 1

    fun show(): Int = a
}

fun outer() {
    val x = 1
    println(x)
}

// expect-clean
