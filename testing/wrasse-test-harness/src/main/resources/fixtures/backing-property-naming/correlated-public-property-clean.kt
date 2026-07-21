package sample

class Foo {
    private val _bar = 1
    val bar: Int get() = _bar
}

// expect-clean
