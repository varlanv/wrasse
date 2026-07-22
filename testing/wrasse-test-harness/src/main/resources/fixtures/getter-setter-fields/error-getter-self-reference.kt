package sample

class Foo {
    val name: String
        get() = name
}

// expect-error 5:9 getter-setter-fields "Property accessor references its own property's name; use 'field' instead to avoid infinite recursion"
