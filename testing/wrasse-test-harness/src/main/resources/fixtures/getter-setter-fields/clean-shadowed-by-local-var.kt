package sample

class Foo {
    val name: String
        get() {
            val name = "local"
            return name
        }
}

// expect-clean
