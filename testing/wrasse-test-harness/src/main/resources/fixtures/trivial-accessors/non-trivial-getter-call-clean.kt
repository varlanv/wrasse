package sample

class Foo {
    val prop: Int = 0
        get() { return someLogic(field) }
}

fun someLogic(x: Int): Int = x

// expect-clean
