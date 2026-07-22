package sample

class Foo {
    val elementList: List<Int>
        get() = _elementList

    companion object {
        private val _elementList = mutableListOf<Int>()
    }
}

// expect-clean
