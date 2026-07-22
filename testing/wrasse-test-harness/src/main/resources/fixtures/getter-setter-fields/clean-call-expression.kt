package sample

class Foo {
    val name: String
        get() = name()
}

fun name(): String = "computed"

// expect-clean
