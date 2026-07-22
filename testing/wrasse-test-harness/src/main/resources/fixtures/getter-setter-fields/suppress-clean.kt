package sample

class Foo {
    @Suppress("getter-setter-fields")
    val name: String
        get() = name
}

// expect-clean
