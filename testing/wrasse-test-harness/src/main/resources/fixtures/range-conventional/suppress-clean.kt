package sample

class Foo {
    @Suppress("range-conventional")
    fun bar() {
        val a = 1
        val b = 5
        val r = a.rangeTo(b)
    }
}

// expect-clean
