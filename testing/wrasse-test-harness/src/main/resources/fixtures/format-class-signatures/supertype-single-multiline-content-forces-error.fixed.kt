package sample

open class Base(val fn: () -> Unit)

class Foo(a: Int) :
    Base(
        {
            println("a")
            println("b")
        },
    )