package sample

class Foo {
    private val _bar = 1

    fun getBar(): Int {
        return _bar
    }
}

// expect-clean
