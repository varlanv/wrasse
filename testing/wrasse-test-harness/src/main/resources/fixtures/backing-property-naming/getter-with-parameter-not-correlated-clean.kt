package sample

class Foo {
    private val _elementList = mutableListOf<Int>()

    fun getElementList(bar: String): List<Int> = _elementList
}

// expect-clean
