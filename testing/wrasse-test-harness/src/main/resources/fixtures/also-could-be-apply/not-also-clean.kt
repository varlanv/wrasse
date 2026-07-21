package sample

class Buzz {
    fun init() {}
    fun block() {}
}

fun make(): Buzz = Buzz().let {
    it.init()
    it.block()
    it
}

// expect-clean
