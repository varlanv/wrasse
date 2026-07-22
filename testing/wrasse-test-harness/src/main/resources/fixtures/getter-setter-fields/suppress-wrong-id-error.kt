package sample

class Foo {
    @Suppress("no-such-rule")
    val name: String
        get() = name
}

// expect-error 6:9 getter-setter-fields "Property accessor references its own property's name; use 'field' instead to avoid infinite recursion"
