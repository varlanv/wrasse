package sample

class Buzz {
    fun init() {}
}

fun doSomethingElse() {}

fun make(): Buzz = Buzz().also {
    it.init()
    doSomethingElse()
}

// expect-clean
