package sample

class Buzz {
    fun init() {}
    fun block() {}
}

@Suppress("also-could-be-apply")
fun make(): Buzz = Buzz().also {
    it.init()
    it.block()
}

// expect-clean
