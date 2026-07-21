package sample

class Buzz {
    fun init() {}
    fun block() {}
}

@Suppress("no-semicolons")
fun make(): Buzz = Buzz().also {
    it.init()
    it.block()
}

// expect-error 9:27 also-could-be-apply "This 'also' block contains only 'it'-qualified statements; consider 'apply' instead"
