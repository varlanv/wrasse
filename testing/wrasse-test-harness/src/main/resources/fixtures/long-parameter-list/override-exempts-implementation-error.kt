package sample

interface Base {
    fun f(a: Int, b: Int, c: Int, d: Int, e: Int, g: Int)
}

class Impl : Base {
    override fun f(a: Int, b: Int, c: Int, d: Int, e: Int, g: Int) {
    }
}

// expect-error 4:10 long-parameter-list "The function has 6 parameters; the maximum allowed is 5"
