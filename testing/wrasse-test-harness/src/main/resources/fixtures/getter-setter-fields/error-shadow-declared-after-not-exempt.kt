package sample

class Foo {
    var name: String = ""
        set(value) {
            name = value
            val name = "local"
            println(name)
        }
}

// expect-error 5:9 getter-setter-fields "Property accessor references its own property's name; use 'field' instead to avoid infinite recursion"
