package sample

interface Base {
    val FirstName: String
}

class Foo(override val FirstName: String) : Base

// expect-clean
