package sample

interface Api {
    fun foo(value: Int)
}

class Impl : Api {
    override fun foo(BadName: Int) {
    }
}

// expect-clean
